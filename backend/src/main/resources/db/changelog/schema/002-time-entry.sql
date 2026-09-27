--liquibase formatted sql

--changeset timesheet:002-time-entry
create table time_entry (
    id         bigint generated always as identity primary key,
    project_id bigint not null references project (id),
    work_date  date   not null,
    start_time time   not null,
    end_time   time   not null,
    check (end_time > start_time)
);

create index time_entry_work_date_idx on time_entry (work_date);
