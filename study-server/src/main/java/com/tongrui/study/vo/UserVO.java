package com.tongrui.study.vo;

import com.tongrui.study.entity.User;
import lombok.Data;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String role;
    private Integer status;

    public static UserVO from(User user) {
        UserVO result = new UserVO();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setNickname(user.getNickname());
        result.setEmail(user.getEmail());
        result.setRole(user.getRole());
        result.setStatus(user.getStatus());
        return result;
    }
}
