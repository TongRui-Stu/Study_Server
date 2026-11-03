package com.tongrui.study.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tongrui.study.common.exception.BusinessException;
import com.tongrui.study.dto.LoginRequest;
import com.tongrui.study.dto.RegisterRequest;
import com.tongrui.study.entity.User;
import com.tongrui.study.mapper.UserMapper;
import com.tongrui.study.service.UserService;
import com.tongrui.study.util.JwtUtil;
import com.tongrui.study.vo.LoginVO;
import com.tongrui.study.vo.UserVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public UserVO register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String nickname = request.getNickname().trim();
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (username.isEmpty() || nickname.isEmpty()) {
            throw new BusinessException("用户名和昵称不能为空");
        }
        if (email != null && email.isEmpty()) {
            email = null;
        }

        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw new BusinessException(409, "用户名已存在");
        }
        if (email != null && userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException(409, "邮箱已被使用");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setRole("STUDENT");
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "用户名或邮箱已存在");
        }
        return UserVO.from(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername().trim()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(400, "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }
        return new LoginVO(jwtUtil.generateToken(user.getId()), UserVO.from(user));
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(401, "登录已失效");
        }
        return UserVO.from(user);
    }
}
