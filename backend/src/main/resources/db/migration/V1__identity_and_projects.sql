CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    global_role VARCHAR(10) NOT NULL CHECK (global_role IN ('ADMIN', 'USER')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (email = LOWER(email))
);
CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(4000) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'ARCHIVED')),
    start_date DATE,
    target_date DATE,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (start_date IS NULL OR target_date IS NULL OR target_date >= start_date)
);
CREATE TABLE project_members (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(10) NOT NULL CHECK (role IN ('MANAGER', 'MEMBER', 'VIEWER')),
    UNIQUE (project_id, user_id)
);
CREATE INDEX project_members_user_project ON project_members(user_id, project_id);
CREATE INDEX projects_status ON projects(status);
CREATE INDEX projects_creator ON projects(created_by);
