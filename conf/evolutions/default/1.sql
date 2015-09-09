# --- rooms and users
 
# --- !ups

create table rooms (
    id bigint not null auto_increment,
    name varchar2(30) not null default id,
    primary key (id)
);

create table users (
    id bigint not null auto_increment,
    room bigint not null,
    name varchar2(30) not null default id,
    joined timestamp not null default sysdate,
    admin boolean not null default false,
    banned boolean not null defalse false,
    primary key (id, room)
    foreign key (room) references (rooms.id)
);  
 
# --- !Downs

drop table rooms;
drop table users;