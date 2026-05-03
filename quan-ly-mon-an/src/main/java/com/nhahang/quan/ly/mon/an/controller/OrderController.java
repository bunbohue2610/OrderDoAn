package com.nhahang.quan.ly.mon.an.controller;
import com.nhahang.quan.ly.mon.an.dto.ApiResponse;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class OrderController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 1. Lay danh sach ban
    @GetMapping("/orders/danh-sach-ban")
    public List<Map<String, Object>> getBans(@RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        if (userId == null) {
            return java.util.Collections.emptyList();
        }
        
        String sql = "SELECT b.id, b.so_ban, b.trang_thai, "
            + "COALESCE(SUM(ct.so_luong), 0) AS so_mon, "
            + "COALESCE(SUM(ct.don_gia * ct.so_luong), 0) AS tam_tinh "
            + "FROM Ban b "
            + "LEFT JOIN don_hang dh ON dh.id_ban = b.id AND dh.trang_thai_thanh_toan = 'CHUA_THANH_TOAN' "
            + "LEFT JOIN chi_tiet_don_hang ct ON ct.id_don_hang = dh.id "
            + "WHERE b.tai_khoan_id = ? "
            + "GROUP BY b.id, b.so_ban, b.trang_thai "
            + "ORDER BY b.id";
        return jdbcTemplate.queryForList(sql, userId);
    }

    // 2. Gui don
    @PostMapping("/orders/gui-don")
    public ResponseEntity<ApiResponse<Map<String, Object>>> guiDon(
            @RequestHeader(value = "X-User-Id", required = false) Integer userId,
            @RequestBody Map<String, Object> payload) {
        try {
            String soBanStr = payload.get("soBan").toString().trim();
            double tongTien = Double.parseDouble(payload.get("tongTien").toString());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");

            String sqlFindId;
            List<Map<String, Object>> tables;
            if (userId != null) {
                sqlFindId = "SELECT id FROM Ban WHERE LTRIM(RTRIM(so_ban)) = ? AND tai_khoan_id = ?";
                tables = jdbcTemplate.queryForList(sqlFindId, soBanStr, userId);
            } else {
                sqlFindId = "SELECT id FROM Ban WHERE LTRIM(RTRIM(so_ban)) = ?";
                tables = jdbcTemplate.queryForList(sqlFindId, soBanStr);
            }

            if (tables.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.fail("❌ Không tìm thấy bàn '" + soBanStr + "'!"));
            }
            int idBanThucTe = ((Number) tables.get(0).get("id")).intValue();

            jdbcTemplate.update("UPDATE Ban SET trang_thai = 'CO_KHACH' WHERE id = ?", idBanThucTe);

            String sqlFindOrder;
            List<Map<String, Object>> existingOrders;
            if (userId != null) {
                sqlFindOrder = "SELECT TOP 1 dh.id FROM don_hang dh "
                    + "JOIN Ban b ON dh.id_ban = b.id "
                    + "WHERE dh.id_ban = ? AND dh.trang_thai_thanh_toan = 'CHUA_THANH_TOAN' "
                    + "AND b.tai_khoan_id = ? ORDER BY dh.ngay_tao DESC";
                existingOrders = jdbcTemplate.queryForList(sqlFindOrder, idBanThucTe, userId);
            } else {
                sqlFindOrder = "SELECT TOP 1 id FROM don_hang WHERE id_ban = ? AND trang_thai_thanh_toan = 'CHUA_THANH_TOAN' ORDER BY ngay_tao DESC";
                existingOrders = jdbcTemplate.queryForList(sqlFindOrder, idBanThucTe);
            }

            long idDonHang;
            if (!existingOrders.isEmpty()) {
                idDonHang = ((Number) existingOrders.get(0).get("id")).longValue();
                jdbcTemplate.update("UPDATE don_hang SET tong_tien = tong_tien + ? WHERE id = ?", tongTien, idDonHang);
            } else {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                String sqlInsert;
                if (userId != null) {
                    sqlInsert = "INSERT INTO don_hang (ngay_tao, tong_tien, trang_thai_don, trang_thai_thanh_toan, id_ban, tai_khoan_id) "
                        + "VALUES (GETDATE(), ?, 'CHO_DUYET', 'CHUA_THANH_TOAN', ?, ?)";
                } else {
                    sqlInsert = "INSERT INTO don_hang (ngay_tao, tong_tien, trang_thai_don, trang_thai_thanh_toan, id_ban) "
                        + "VALUES (GETDATE(), ?, 'CHO_DUYET', 'CHUA_THANH_TOAN', ?)";
                }
                
                if (userId != null) {
                    final Integer finalUserId = userId;
                    jdbcTemplate.update(connection -> {
                        java.sql.PreparedStatement ps = connection.prepareStatement(sqlInsert, new String[]{"id"});
                        ps.setDouble(1, tongTien);
                        ps.setInt(2, idBanThucTe);
                        ps.setInt(3, finalUserId);
                        return ps;
                    }, keyHolder);
                } else {
                    jdbcTemplate.update(connection -> {
                        java.sql.PreparedStatement ps = connection.prepareStatement(sqlInsert, new String[]{"id"});
                        ps.setDouble(1, tongTien);
                        ps.setInt(2, idBanThucTe);
                        return ps;
                    }, keyHolder);
                }
                idDonHang = keyHolder.getKey().longValue();
            }

            for (Map<String, Object> item : items) {
                Object idMonAnObj = item.get("idMonAn");
                if (idMonAnObj == null) idMonAnObj = item.get("id");
                Object donGia = item.get("giaTien");
                if (donGia == null) donGia = item.get("donGia");

                jdbcTemplate.update(
                    "INSERT INTO chi_tiet_don_hang (id_don_hang, so_luong, don_gia, id_mon_an) VALUES (?, ?, ?, ?)",
                    idDonHang, item.get("soLuong"), donGia, idMonAnObj
                );
            }

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("soBan", soBanStr);
            result.put("tongTien", tongTien);
            return ResponseEntity.ok(ApiResponse.ok("✅ Gửi đơn thành công!", result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail("❌ Lỗi server: " + e.getMessage()));
        }
    }

    // 3. Chi tiet ban
    @GetMapping("/orders/chi-tiet-ban/{soBan}")
    public List<Map<String, Object>> getChiTietTheoBan(
            @PathVariable String soBan,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        String sql;
        if (userId != null) {
            sql = "SELECT ct.id, ct.so_luong, ct.don_gia AS gia_tien, m.ten_mon, ct.id_mon_an "
                + "FROM chi_tiet_don_hang ct "
                + "JOIN don_hang dh ON ct.id_don_hang = dh.id "
                + "JOIN Ban b ON dh.id_ban = b.id "
                + "JOIN mon_an m ON ct.id_mon_an = m.id "
                + "WHERE b.so_ban = ? AND dh.trang_thai_thanh_toan = 'CHUA_THANH_TOAN' AND b.tai_khoan_id = ?";
            return jdbcTemplate.queryForList(sql, soBan, userId);
        } else {
            sql = "SELECT ct.id, ct.so_luong, ct.don_gia AS gia_tien, m.ten_mon, ct.id_mon_an "
                + "FROM chi_tiet_don_hang ct "
                + "JOIN don_hang dh ON ct.id_don_hang = dh.id "
                + "JOIN Ban b ON dh.id_ban = b.id "
                + "JOIN mon_an m ON ct.id_mon_an = m.id "
                + "WHERE b.so_ban = ? AND dh.trang_thai_thanh_toan = 'CHUA_THANH_TOAN'";
            return jdbcTemplate.queryForList(sql, soBan);
        }
    }

    // 4. Thanh toan
    @PostMapping("/orders/thanh-toan/{soBan}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> thanhToan(
            @PathVariable String soBan,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId,
            @RequestBody Map<String, Object> payload) {
        try {
            String soBanTrimmed = soBan.trim();
            double tongTienThucTe = Double.parseDouble(payload.get("tongTien").toString());

            String sqlGetId;
            List<Map<String, Object>> orders;
            if (userId != null) {
                sqlGetId = "SELECT dh.id FROM don_hang dh JOIN Ban b ON dh.id_ban = b.id "
                    + "WHERE LTRIM(RTRIM(b.so_ban)) = ? AND dh.trang_thai_thanh_toan = 'CHUA_THANH_TOAN' AND b.tai_khoan_id = ?";
                orders = jdbcTemplate.queryForList(sqlGetId, soBanTrimmed, userId);
            } else {
                sqlGetId = "SELECT dh.id FROM don_hang dh JOIN Ban b ON dh.id_ban = b.id "
                    + "WHERE LTRIM(RTRIM(b.so_ban)) = ? AND dh.trang_thai_thanh_toan = 'CHUA_THANH_TOAN'";
                orders = jdbcTemplate.queryForList(sqlGetId, soBanTrimmed);
            }

            if (!orders.isEmpty()) {
                int idDon = ((Number) orders.get(0).get("id")).intValue();

                jdbcTemplate.update(
                    "UPDATE don_hang SET trang_thai_thanh_toan = 'DA_THANH_TOAN', trang_thai_don = 'DA_PHUC_VU', tong_tien = ? WHERE id = ?",
                    tongTienThucTe, idDon);

                jdbcTemplate.update("UPDATE Ban SET trang_thai = 'TRONG' WHERE LTRIM(RTRIM(so_ban)) = ?", soBanTrimmed);
                
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("soBan", soBanTrimmed);
                result.put("tongTien", tongTienThucTe);
                return ResponseEntity.ok(ApiResponse.ok("✅ Thanh toán thành công!", result));
            }
            return ResponseEntity.badRequest().body(ApiResponse.fail("❌ Không tìm thấy đơn hàng để thanh toán!"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail("❌ Lỗi server: " + e.getMessage()));
        }
    }

    // 5. Khoi tao so do ban hang loat
    @PostMapping("/orders/khoi-tao-so-do")
    public ResponseEntity<ApiResponse<Map<String, Object>>> khoiTaoSoDo(
            @RequestHeader(value = "X-User-Id", required = false) Integer userId,
            @RequestBody Map<String, Integer> payload) {
        try {
            int soLuong = payload.get("soLuong");
            if (userId != null) {
                jdbcTemplate.update("DELETE FROM chi_tiet_don_hang WHERE id_don_hang IN (SELECT id FROM don_hang WHERE id_ban IN (SELECT id FROM Ban WHERE tai_khoan_id = ?))", userId);
                jdbcTemplate.update("DELETE FROM don_hang WHERE id_ban IN (SELECT id FROM Ban WHERE tai_khoan_id = ?)", userId);
                jdbcTemplate.update("DELETE FROM Ban WHERE tai_khoan_id = ?", userId);
            } else {
                jdbcTemplate.update("DELETE FROM chi_tiet_don_hang");
                jdbcTemplate.update("DELETE FROM don_hang");
                jdbcTemplate.update("DELETE FROM Ban");
            }
            for (int i = 1; i <= soLuong; i++) {
                String tenBan = String.format("B%02d", i);
                if (userId != null) {
                    jdbcTemplate.update("INSERT INTO Ban (so_ban, trang_thai, tai_khoan_id) VALUES (?, 'TRONG', ?)", tenBan, userId);
                } else {
                    jdbcTemplate.update("INSERT INTO Ban (so_ban, trang_thai) VALUES (?, 'TRONG')", tenBan);
                }
            }
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("soLuong", soLuong);
            return ResponseEntity.ok(ApiResponse.ok("✅ Khởi tạo " + soLuong + " bàn thành công!", result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail("❌ Lỗi: " + e.getMessage()));
        }
    }

    // 6. Cap nhat mon tai ban khi admin them/bot mon
    @PostMapping("/orders/cap-nhat-ban/{soBan}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> capNhatBan(
            @PathVariable String soBan,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId,
            @RequestBody List<Map<String, Object>> items) {
        try {
            String sqlFind;
            List<Map<String, Object>> orders;
            if (userId != null) {
                sqlFind = "SELECT dh.id FROM don_hang dh "
                    + "JOIN Ban b ON dh.id_ban = b.id "
                    + "WHERE LTRIM(RTRIM(b.so_ban)) = ? AND dh.trang_thai_thanh_toan = 'CHUA_THANH_TOAN' AND b.tai_khoan_id = ?";
                orders = jdbcTemplate.queryForList(sqlFind, soBan.trim(), userId);
            } else {
                sqlFind = "SELECT dh.id FROM don_hang dh "
                    + "JOIN Ban b ON dh.id_ban = b.id "
                    + "WHERE LTRIM(RTRIM(b.so_ban)) = ? AND dh.trang_thai_thanh_toan = 'CHUA_THANH_TOAN'";
                orders = jdbcTemplate.queryForList(sqlFind, soBan.trim());
            }

            if (orders.isEmpty()) {
                String sqlFindBan;
                List<Map<String, Object>> bans;
                if (userId != null) {
                    sqlFindBan = "SELECT id FROM Ban WHERE LTRIM(RTRIM(so_ban)) = ? AND tai_khoan_id = ?";
                    bans = jdbcTemplate.queryForList(sqlFindBan, soBan.trim(), userId);
                } else {
                    sqlFindBan = "SELECT id FROM Ban WHERE LTRIM(RTRIM(so_ban)) = ?";
                    bans = jdbcTemplate.queryForList(sqlFindBan, soBan.trim());
                }
                if (bans.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.fail("❌ Không tìm thấy bàn!"));

                int idBan = ((Number) bans.get(0).get("id")).intValue();
                jdbcTemplate.update("UPDATE Ban SET trang_thai = 'CO_KHACH' WHERE id = ?", idBan);

                KeyHolder keyHolder = new GeneratedKeyHolder();
                String sqlInsertDon;
                if (userId != null) {
                    sqlInsertDon = "INSERT INTO don_hang (ngay_tao, tong_tien, trang_thai_don, trang_thai_thanh_toan, id_ban, tai_khoan_id) "
                        + "VALUES (GETDATE(), 0, 'CHO_DUYET', 'CHUA_THANH_TOAN', ?, ?)";
                } else {
                    sqlInsertDon = "INSERT INTO don_hang (ngay_tao, tong_tien, trang_thai_don, trang_thai_thanh_toan, id_ban) "
                        + "VALUES (GETDATE(), 0, 'CHO_DUYET', 'CHUA_THANH_TOAN', ?)";
                }

                if (userId != null) {
                    final Integer finalUserId = userId;
                    jdbcTemplate.update(conn -> {
                        java.sql.PreparedStatement ps = conn.prepareStatement(sqlInsertDon, new String[]{"id"});
                        ps.setInt(1, idBan);
                        ps.setInt(2, finalUserId);
                        return ps;
                    }, keyHolder);
                } else {
                    jdbcTemplate.update(conn -> {
                        java.sql.PreparedStatement ps = conn.prepareStatement(sqlInsertDon, new String[]{"id"});
                        ps.setInt(1, idBan);
                        return ps;
                    }, keyHolder);
                }

                long idDon = keyHolder.getKey().longValue();
                for (Map<String, Object> item : items) {
                    jdbcTemplate.update(
                        "INSERT INTO chi_tiet_don_hang (id_don_hang, so_luong, don_gia, id_mon_an) VALUES (?, ?, ?, ?)",
                        idDon, item.get("so_luong"), item.get("gia_tien"), item.get("mon_an_id")
                    );
                }
                jdbcTemplate.update(
                    "UPDATE don_hang SET tong_tien = (SELECT COALESCE(SUM(so_luong * don_gia), 0) FROM chi_tiet_don_hang WHERE id_don_hang = ?) WHERE id = ?",
                    idDon, idDon
                );
            } else {
                long idDon = ((Number) orders.get(0).get("id")).longValue();
                jdbcTemplate.update("DELETE FROM chi_tiet_don_hang WHERE id_don_hang = ?", idDon);
                for (Map<String, Object> item : items) {
                    jdbcTemplate.update(
                        "INSERT INTO chi_tiet_don_hang (id_don_hang, so_luong, don_gia, id_mon_an) VALUES (?, ?, ?, ?)",
                        idDon, item.get("so_luong"), item.get("gia_tien"), item.get("mon_an_id")
                    );
                }
                jdbcTemplate.update(
                    "UPDATE don_hang SET tong_tien = (SELECT COALESCE(SUM(so_luong * don_gia), 0) FROM chi_tiet_don_hang WHERE id_don_hang = ?) WHERE id = ?",
                    idDon, idDon
                );
            }
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("soBan", soBan.trim());
            return ResponseEntity.ok(ApiResponse.ok("✅ Cập nhật bàn thành công!", result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail("❌ Lỗi: " + e.getMessage()));
        }
    }

    // 7. Xoa ban
    @DeleteMapping("/orders/xoa-ban/{soBan}")
    public ResponseEntity<ApiResponse<Void>> xoaBan(
            @PathVariable String soBan,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        try {
            soBan = soBan.trim();

            String sqlCheckBan;
            List<Map<String, Object>> bans;
            if (userId != null) {
                sqlCheckBan = "SELECT id FROM Ban WHERE LTRIM(RTRIM(so_ban)) = ? AND tai_khoan_id = ?";
                bans = jdbcTemplate.queryForList(sqlCheckBan, soBan, userId);
            } else {
                sqlCheckBan = "SELECT id FROM Ban WHERE LTRIM(RTRIM(so_ban)) = ?";
                bans = jdbcTemplate.queryForList(sqlCheckBan, soBan);
            }

            if (bans.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.fail("❌ Không tìm thấy bàn " + soBan));
            }

            int idBan = ((Number) bans.get(0).get("id")).intValue();

            jdbcTemplate.update("DELETE FROM chi_tiet_don_hang WHERE id_don_hang IN (SELECT id FROM don_hang WHERE id_ban = ?)", idBan);
            jdbcTemplate.update("DELETE FROM don_hang WHERE id_ban = ?", idBan);
            jdbcTemplate.update("DELETE FROM Ban WHERE id = ?", idBan);

            return ResponseEntity.ok(ApiResponse.ok("✅ Đã xóa bàn " + soBan + " thành công!", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.fail("❌ Lỗi server: " + e.getMessage()));
        }
    }

    // 8. [MỚI] Thong ke hom nay
    @GetMapping("/orders/thong-ke-hom-nay")
    public Map<String, Object> thongKeHomNay(
            @RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            Double doanhThu;
            Integer soDon;
            if (userId != null) {
                doanhThu = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(tong_tien), 0) FROM don_hang "
                    + "WHERE trang_thai_thanh_toan = 'DA_THANH_TOAN' "
                    + "AND CAST(ngay_tao AS DATE) = CAST(GETDATE() AS DATE) "
                    + "AND tai_khoan_id = ?",
                    Double.class, userId);
                soDon = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM don_hang "
                    + "WHERE trang_thai_thanh_toan = 'DA_THANH_TOAN' "
                    + "AND CAST(ngay_tao AS DATE) = CAST(GETDATE() AS DATE) "
                    + "AND tai_khoan_id = ?",
                    Integer.class, userId);
            } else {
                doanhThu = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(tong_tien), 0) FROM don_hang "
                    + "WHERE trang_thai_thanh_toan = 'DA_THANH_TOAN' "
                    + "AND CAST(ngay_tao AS DATE) = CAST(GETDATE() AS DATE)",
                    Double.class);
                soDon = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM don_hang "
                    + "WHERE trang_thai_thanh_toan = 'DA_THANH_TOAN' "
                    + "AND CAST(ngay_tao AS DATE) = CAST(GETDATE() AS DATE)",
                    Integer.class);
            }
            result.put("doanhThu", doanhThu != null ? doanhThu : 0);
            result.put("soDon", soDon != null ? soDon : 0);
        } catch (Exception e) {
            result.put("doanhThu", 0);
            result.put("soDon", 0);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // 9. [MỚI] Reset ban (xoa mon, giu lai ban, ve trang thai TRONG)
    @PostMapping("/orders/reset-ban/{soBan}")
    public ResponseEntity<ApiResponse<Void>> resetBan(
            @PathVariable String soBan,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        try {
            String soBanTrimmed = soBan.trim();

            String sqlFind;
            List<Map<String, Object>> orders;
            if (userId != null) {
                sqlFind = "SELECT dh.id FROM don_hang dh JOIN Ban b ON dh.id_ban = b.id "
                    + "WHERE LTRIM(RTRIM(b.so_ban)) = ? "
                    + "AND dh.trang_thai_thanh_toan = 'CHUA_THANH_TOAN' "
                    + "AND b.tai_khoan_id = ?";
                orders = jdbcTemplate.queryForList(sqlFind, soBanTrimmed, userId);
            } else {
                sqlFind = "SELECT dh.id FROM don_hang dh JOIN Ban b ON dh.id_ban = b.id "
                    + "WHERE LTRIM(RTRIM(b.so_ban)) = ? "
                    + "AND dh.trang_thai_thanh_toan = 'CHUA_THANH_TOAN'";
                orders = jdbcTemplate.queryForList(sqlFind, soBanTrimmed);
            }

            if (!orders.isEmpty()) {
                int idDon = ((Number) orders.get(0).get("id")).intValue();
                jdbcTemplate.update("DELETE FROM chi_tiet_don_hang WHERE id_don_hang = ?", idDon);
                jdbcTemplate.update("DELETE FROM don_hang WHERE id = ?", idDon);
            }

            jdbcTemplate.update(
                "UPDATE Ban SET trang_thai = 'TRONG' WHERE LTRIM(RTRIM(so_ban)) = ?",
                soBanTrimmed
            );

            return ResponseEntity.ok(ApiResponse.ok("✅ Reset bàn thành công!", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("❌ Lỗi: " + e.getMessage()));
        }
    }
}