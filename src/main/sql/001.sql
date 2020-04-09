create table users (
                         id UUID primary key,
                         email varchar not null,
                         created_at timestamp not null
  );

create table games (
                       id UUID primary key,
                       created_at timestamp not null,
                       user1 UUID references users(id),
                       user2 UUID references users(id),
                       field_height integer not null,
                       field_width integer not null,
                       winning_condition integer not null,
                       status json not null
);

create table moves (
                       id UUID primary key,
                       created_at timestamp not null,
                       game_id UUID references games(id) not null,
                       user_id UUID references users(id) not null,
                       row integer not null,
                       "column" integer not null
);