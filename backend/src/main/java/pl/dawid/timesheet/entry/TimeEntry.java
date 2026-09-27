package pl.dawid.timesheet.entry;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    protected TimeEntry() {
    }

    public TimeEntry(Long projectId, LocalDate workDate, TimeSlot slot) {
        change(projectId, workDate, slot);
    }

    public void change(Long projectId, LocalDate workDate, TimeSlot slot) {
        this.projectId = projectId;
        this.workDate = workDate;
        this.startTime = slot.start();
        this.endTime = slot.end();
    }

    public TimeSlot slot() {
        return new TimeSlot(startTime, endTime);
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }
}
