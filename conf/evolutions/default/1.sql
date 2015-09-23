# --- rooms and users
 
# --- !ups

create table rooms (
    roomId bigint not null auto_increment,
    roomName varchar2 not null default roomId,
    primary key (roomId)
);

create table users (
    actorPath varchar2 not null,
    roomId bigint not null,
    userName varchar2 not null default sysdate,
    joinDate timestamp not null default sysdate,
    isAdmin boolean not null default false,
    isBanned boolean not null default false,
    primary key (actorPath, roomId)
    foreign key (roomId) references (rooms.roomId)
);  
 
# --- !Downs

drop table rooms;
drop table users;