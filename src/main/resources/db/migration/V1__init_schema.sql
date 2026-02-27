create table if not exists users (
    id bigserial primary key,
    api_key varchar(128) not null unique,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists conversations (
    id bigserial primary key,
    user_id bigint not null references users(id) on delete cascade,
    title varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists messages (
    id bigserial primary key,
    conversation_id bigint not null references conversations(id) on delete cascade,
    role varchar(20) not null,
    content text not null,
    created_at timestamptz not null
);

create index if not exists idx_conversations_user_updated
    on conversations(user_id, updated_at desc);

create index if not exists idx_messages_conversation_created
    on messages(conversation_id, created_at asc);
