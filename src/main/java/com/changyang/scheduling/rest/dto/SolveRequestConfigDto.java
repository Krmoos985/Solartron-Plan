package com.changyang.scheduling.rest.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SolveRequestConfigDto {

    private ConstraintSelectionDto constraints = new ConstraintSelectionDto();
    private TerminationSettingsDto termination = new TerminationSettingsDto();
}
