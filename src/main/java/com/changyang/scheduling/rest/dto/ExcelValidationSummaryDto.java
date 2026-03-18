package com.changyang.scheduling.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExcelValidationSummaryDto {

    private String sourceName;
    private Map<String, Object> metrics = new LinkedHashMap<>();
    private List<ConstraintStatusDto> constraintStatus = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
