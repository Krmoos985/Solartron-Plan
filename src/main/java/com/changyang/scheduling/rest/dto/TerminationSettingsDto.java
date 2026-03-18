package com.changyang.scheduling.rest.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TerminationSettingsDto {

    private Integer timeLimitSeconds = 60;
    private Integer unimprovedTimeLimitSeconds;
    private Integer stepCountLimit;
    private Boolean stopOnFeasible = Boolean.FALSE;
}
