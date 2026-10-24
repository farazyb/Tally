alter table wallet drop column status;
alter table wallet drop column updated_at;
alter table wallet alter column account_number set not null;
