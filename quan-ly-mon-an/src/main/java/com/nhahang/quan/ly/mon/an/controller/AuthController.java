package com.nhahang.quan.ly.mon.an.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nhahang.quan.ly.mon.an.dto.ApiResponse;
import com.nhahang.quan.ly.mon.an.entity.TaiKhoan;
import com.nhahang.quan.ly.mon.an.repository.TaiKhoanRepository;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
public class AuthController {

    @Autowired
    private TaiKhoanRepository taiKhoanRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");

        // 1. Tìm tài khoản trong Database
        Optional<TaiKhoan> userOpt = taiKhoanRepository.findByUsername(username);

        // 2. Nếu tài khoản tồn tại
        if (userOpt.isPresent()) {
            TaiKhoan user = userOpt.get();
            // 3. So sánh mật khẩu (Chưa mã hóa)
            if (user.getPassword().equals(password)) {
                // Trả về thông tin user kèm ID để frontend có thể gửi lên server
                Map<String, Object> response = new HashMap<>();
                response.put("id", user.getId());
                response.put("username", user.getUsername());
                response.put("hoTen", user.getHoTen());
                response.put("vaiTro", user.getVaiTro());
                
                return ResponseEntity.ok(new ApiResponse<>(true, "Đăng nhập thành công", response));
            }
        }
        
        // Trả về lỗi nếu sai tên hoặc sai pass
        return ResponseEntity.status(401).body(new ApiResponse<>(false, "Sai tài khoản hoặc mật khẩu", null));
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String hoTen = payload.get("hoTen");

        // 1. Kiểm tra xem tên đăng nhập này đã có ai xài chưa
        if (taiKhoanRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Tên đăng nhập này đã tồn tại!", null));
        }

        // 2. Nếu chưa ai xài thì tạo mới
        TaiKhoan newAcc = new TaiKhoan();
        newAcc.setUsername(username);
        newAcc.setPassword(password);
        newAcc.setHoTen(hoTen);
        newAcc.setVaiTro("ADMIN"); // Mặc định cấp quyền Admin luôn

        // 3. Lưu thẳng xuống SQL Server
        taiKhoanRepository.save(newAcc);

        return ResponseEntity.ok(new ApiResponse<>(true, "Đăng ký thành công", null));
    }
}