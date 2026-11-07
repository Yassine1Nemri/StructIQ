CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE')),
    priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    assignee_id UUID,
    due_date DATE,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tasks_assignee_membership FOREIGN KEY (project_id, assignee_id)
        REFERENCES project_members(project_id, user_id)
);
CREATE INDEX tasks_project_status ON tasks(project_id, status);
CREATE INDEX tasks_project_due ON tasks(project_id, due_date);
CREATE INDEX tasks_assignee_status ON tasks(assignee_id, status);
