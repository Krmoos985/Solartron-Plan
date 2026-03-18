package com.changyang.scheduling.service;

import com.changyang.scheduling.solver.SchedulingConstraintConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConstraintModelStatusLogger implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        SchedulingConstraintConfiguration defaults = SchedulingConstraintConfiguration.defaults();

        log.info("=== Constraint model status on startup ===");
        log.info(
                "Default enabled constraints ({}): {}",
                defaults.describeEnabledConstraints().size(),
                String.join(", ", defaults.describeEnabledConstraints())
        );
        log.info(
                "Implemented but disabled by default ({}): {}",
                defaults.describeDisabledConstraints().size(),
                String.join(", ", defaults.describeDisabledConstraints())
        );
        log.info(
                "Current Excel mode supported constraints ({}): {}",
                SchedulingConstraintConfiguration.currentExcelModeConstraintCodes().size(),
                String.join(", ", SchedulingConstraintConfiguration.currentExcelModeConstraintCodes())
        );
        log.info(
                "Current Excel mode locked constraints ({}): {}",
                SchedulingConstraintConfiguration.splitLockedConstraintCodes().size(),
                String.join(", ", SchedulingConstraintConfiguration.splitLockedConstraintCodes())
        );
        log.info("Model note: current Excel mode keeps original order granularity and does not enable split-task constraints.");
    }
}
