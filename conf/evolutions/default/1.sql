# --- rooms and users
 
# --- !ups

create table rooms (
    roomId bigint not null auto_increment,
    roomName varchar2 not null default roomId,
    primary key (roomId)
);

create table users (
    userId bigint not null auto_increment,
    actorName varchar2 not null,
    actorPath varchar2 not null,
    roomId bigint not null,
    userName varchar2 not null default userId,
    joinDate timestamp not null default sysdate,
    isAdmin boolean not null default false,
    primary key (userId),
    foreign key (roomId) references (rooms.roomId)
);  
 
# --- !Downs

drop table rooms;
drop table users;