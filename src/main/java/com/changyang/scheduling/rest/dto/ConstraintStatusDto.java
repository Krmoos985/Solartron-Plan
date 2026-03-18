package com.changyang.scheduling.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConstraintStatusDto {

    private String id;
    private String label;
    private String scoreLevel;
    private boolean available;
    private boolean dataReady;
    private boolean enabledByDefault;
    private boolean recommended;
    private String note;
}
