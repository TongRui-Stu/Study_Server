package com.tongrui.study.service;

import com.tongrui.study.dto.LoginRequest;
import com.tongrui.study.dto.RegisterRequest;
import com.tongrui.study.vo.LoginVO;
import com.tongrui.study.vo.UserVO;

public interface UserService {

    UserVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    UserVO getCurrentUser(Long userId);
}
