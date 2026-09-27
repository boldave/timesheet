package pl.dawid.timesheet;

import org.springframework.boot.SpringApplication;

public class TestTimesheetApplication {

	public static void main(String[] args) {
		SpringApplication.from(TimesheetApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
