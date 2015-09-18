# --- rooms and users
 
# --- !ups

create table rooms (
    id bigint not null auto_increment,
    name varchar2 not null default id,
    primary key (id)
);

create table users (
    path varchar2 not null,
    room bigint not null,
    name varchar2 not null default sysdate,
    joined timestamp not null default sysdate,
    admin boolean not null default false,
    banned boolean not null default false,
    primary key (path, room)
    foreign key (room) references (rooms.id)
);  
 
# --- !Downs

drop table rooms;
drop table users;