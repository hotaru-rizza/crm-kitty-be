-- Appointments (heaviest table — calendar, board, list views)
CREATE INDEX IF NOT EXISTS idx_appointment_tenant_start ON appointments (tenant_id, start_time);
CREATE INDEX IF NOT EXISTS idx_appointment_tenant_deleted ON appointments (tenant_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_appointment_artist ON appointments (artist_id);
CREATE INDEX IF NOT EXISTS idx_appointment_client ON appointments (client_id);
CREATE INDEX IF NOT EXISTS idx_appointment_service ON appointments (service_id);
CREATE INDEX IF NOT EXISTS idx_appointment_location ON appointments (location_id);
CREATE INDEX IF NOT EXISTS idx_appointment_status ON appointments (status);
CREATE INDEX IF NOT EXISTS idx_appointment_start_time ON appointments (start_time);

-- Staff
CREATE INDEX IF NOT EXISTS idx_staff_tenant_deleted ON staff (tenant_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_staff_auth_user ON staff (auth_user_id);
CREATE INDEX IF NOT EXISTS idx_staff_email_tenant ON staff (email, tenant_id);

-- Clients
CREATE INDEX IF NOT EXISTS idx_client_tenant_deleted ON clients (tenant_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_client_phone_tenant ON clients (phone, tenant_id);
CREATE INDEX IF NOT EXISTS idx_client_location ON clients (location_id);

-- Projects
CREATE INDEX IF NOT EXISTS idx_project_tenant_deleted ON projects (tenant_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_project_client ON projects (client_id);
CREATE INDEX IF NOT EXISTS idx_project_artist ON projects (artist_id);
CREATE INDEX IF NOT EXISTS idx_project_status ON projects (status);

-- Requests
CREATE INDEX IF NOT EXISTS idx_request_tenant_status ON requests (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_request_tenant_created ON requests (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_request_location ON requests (location_id);

-- Transactions
CREATE INDEX IF NOT EXISTS idx_transaction_tenant_deleted ON transactions (tenant_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_transaction_appointment ON transactions (appointment_id);
CREATE INDEX IF NOT EXISTS idx_transaction_staff ON transactions (staff_id);
CREATE INDEX IF NOT EXISTS idx_transaction_type ON transactions (type);
CREATE INDEX IF NOT EXISTS idx_transaction_date ON transactions (date);

-- Staff schedules
CREATE INDEX IF NOT EXISTS idx_schedule_staff ON staff_schedules (staff_id);

-- Artist service pricing
CREATE INDEX IF NOT EXISTS idx_asp_staff ON artist_service_pricing (staff_id);
CREATE INDEX IF NOT EXISTS idx_asp_service ON artist_service_pricing (service_id);
