package com.vietnam.pji.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DeleteNotificationsRequestDTO {

    @NotEmpty(message = "ids must not be empty")
    private List<@NotNull(message = "notification id must not be null") Long> ids;
}
