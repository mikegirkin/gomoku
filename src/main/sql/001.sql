create table users (
  id UUID primary key,
  email varchar not null,
  created_at timestamp not null
);

create table games (
  id UUID primary key,
  created_at timestamp not null,
  user_1 UUID references users(id),
  user_2 UUID references users(id),
  winning_condition integer not null,
  status varchar not null
);

create table moves (
  id UUID primary key,
  created_at timestamp not null,
  game_id UUID references games(id) not null,
  is_left_user boolean not null,
  move json not null
);