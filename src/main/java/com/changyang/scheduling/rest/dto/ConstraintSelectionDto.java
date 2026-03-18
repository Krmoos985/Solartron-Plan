package com.changyang.scheduling.rest.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConstraintSelectionDto {

    private boolean hc1 = true;
    private boolean hc2;
    private boolean hc3;
    private boolean hc4;
    private boolean mc1;
    private boolean mc2;
    private boolean sc1 = true;
    private boolean sc2;
    private boolean sc3;
    private boolean sc4;
}
