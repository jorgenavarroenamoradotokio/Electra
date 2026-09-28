package com.redur.electra.data.remote.dto.request.login;

public record LoginRequestDTO(
        String userName,
        String password,
        String language
) {
}
