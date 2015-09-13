# --- persist actor paths

# --- !ups

alter table users
add path varchar2 not null
;

# --- !Downs

alter tables users
drop path
;