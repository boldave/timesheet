--liquibase formatted sql

--changeset timesheet:001-project
create table project (
    id    bigint generated always as identity primary key,
    name  varchar(100) not null unique,
    color varchar(7)   not null check (color ~ '^#[0-9a-fA-F]{6}$')
);

insert into project (name, color) values
    ('Basketo',    '#2563eb'),
    ('Hotelero',   '#16a34a'),
    ('Wewnętrzne', '#9333ea');
