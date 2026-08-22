-- ============================================================
-- Chotobela — Supabase schema
-- Run in Supabase SQL editor when provisioning the backend.
-- The app runs fully in DEMO MODE until this is provisioned.
-- ============================================================

-- ---------- profiles ----------
create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text unique not null check (char_length(username) between 3 and 32),
    avatar text,
    total_playtime_seconds bigint not null default 0,
    games_played int not null default 0,
    created_at timestamptz not null default now()
);

-- ---------- games (catalog) ----------
create table if not exists public.games (
    id text primary key,
    title text not null,
    description text not null default '',
    platform text not null,           -- Arcade | NES | SNES | GB | GBA | Genesis | PSX | CHIP-8
    core text not null,               -- mame | fbneo | chip8 | ...
    version text not null default '1.0.0',
    cover_image text,
    screenshots jsonb not null default '[]'::jsonb,
    download_url text not null,
    file_hash text,                   -- sha256 for download verification
    size bigint not null default 0,
    developer text not null default '',
    year int not null default 0,
    rating double precision not null default 0,
    download_count bigint not null default 0,
    featured boolean not null default false,
    trending boolean not null default false,
    category text not null default 'arcade',
    created_at timestamptz not null default now()
);
create index if not exists games_platform_idx on public.games(platform);
create index if not exists games_category_idx on public.games(category);
create index if not exists games_title_idx on public.games using gin (to_tsvector('simple', title));

-- ---------- favorites ----------
create table if not exists public.favorites (
    user_id uuid not null references auth.users(id) on delete cascade,
    game_id text not null references public.games(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, game_id)
);

-- ---------- downloads ----------
create table if not exists public.downloads (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    game_id text not null references public.games(id) on delete cascade,
    status text not null default 'queued',  -- queued|downloading|paused|completed|failed
    progress numeric not null default 0,
    bytes_done bigint not null default 0,
    bytes_total bigint not null default 0,
    created_at timestamptz not null default now()
);
create index downloads_user_idx on public.downloads(user_id);

-- ---------- reviews ----------
create table if not exists public.reviews (
    id uuid primary key default gen_random_uuid(),
    game_id text not null references public.games(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    rating int not null check (rating between 1 and 5),
    comment text not null default '' check (char_length(comment) <= 2000),
    created_at timestamptz not null default now(),
    unique (game_id, user_id)
);

-- ---------- playtime ----------
create table if not exists public.play_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    game_id text not null references public.games(id) on delete cascade,
    seconds bigint not null default 0,
    played_at timestamptz not null default now()
);

-- ---------- achievements ----------
create table if not exists public.achievements (
    id text primary key,
    game_id text references public.games(id) on delete cascade,
    title text not null,
    description text not null default '',
    points int not null default 10
);

create table if not exists public.user_achievements (
    user_id uuid not null references auth.users(id) on delete cascade,
    achievement_id text not null references public.achievements(id) on delete cascade,
    unlocked_at timestamptz not null default now(),
    primary key (user_id, achievement_id)
);

-- ---------- leaderboards ----------
create table if not exists public.leaderboard (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    game_id text not null references public.games(id) on delete cascade,
    score bigint not null default 0,
    created_at timestamptz not null default now()
);
create index leaderboard_game_idx on public.leaderboard(game_id, score desc);

-- ---------- cloud saves ----------
create table if not exists public.cloud_saves (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    game_id text not null references public.games(id) on delete cascade,
    slot int not null default 0,
    storage_path text not null,
    size_bytes bigint not null default 0,
    updated_at timestamptz not null default now(),
    unique (user_id, game_id, slot)
);

-- ============================================================
-- Row Level Security
-- ============================================================
alter table public.profiles        enable row level security;
alter table public.favorites       enable row level security;
alter table public.downloads       enable row level security;
alter table public.reviews         enable row level security;
alter table public.play_sessions   enable row level security;
alter table public.user_achievements enable row level security;
alter table public.leaderboard     enable row level security;
alter table public.cloud_saves     enable row level security;

-- Catalog readable by anyone authenticated/anon; writable by service role only.
alter table public.games enable row level security;
create policy "games_public_read" on public.games for select using (true);

-- Profiles: self read/update
create policy "profiles_self_select" on public.profiles for select using (auth.uid() = id);
create policy "profiles_self_update" on public.profiles for update using (auth.uid() = id);

-- Favorites: owner-only
create policy "favorites_owner_all" on public.favorites
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Downloads: owner-only
create policy "downloads_owner_all" on public.downloads
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Reviews: read all, write own
create policy "reviews_read" on public.reviews for select using (true);
create policy "reviews_write_own" on public.reviews
    for insert with check (auth.uid() = user_id);
create policy "reviews_update_own" on public.reviews
    for update using (auth.uid() = user_id);
create policy "reviews_delete_own" on public.reviews
    for delete using (auth.uid() = user_id);

-- Playtime: write own, read own
create policy "playtime_owner_all" on public.play_sessions
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Achievements: read all, unlock own
create policy "achievements_read" on public.achievements for select using (true);
create policy "user_achievements_owner_all" on public.user_achievements
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Leaderboard: read all (public board), submit own
create policy "leaderboard_read" on public.leaderboard for select using (true);
create policy "leaderboard_submit_own" on public.leaderboard
    for insert with check (auth.uid() = user_id);

-- Cloud saves: strictly owner-only
create policy "cloud_saves_owner_all" on public.cloud_saves
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Auto-create profile on signup
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer as $$
begin
    insert into public.profiles (id, username)
    values (
        new.id,
        coalesce(new.raw_user_meta_data->>'username', 'player_' || substr(new.id::text, 1, 8))
    )
    on conflict do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();
