ALTER TABLE knowledge_chunk ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_chunk FORCE ROW LEVEL SECURITY;

CREATE POLICY knowledge_chunk_tenant_and_global_isolation ON knowledge_chunk
    USING (
        tenant_id = current_setting('app.tenant_id', true)::uuid
        OR tenant_id = '00000000-0000-0000-0000-000000000000'::uuid
    )
    WITH CHECK (
        tenant_id = current_setting('app.tenant_id', true)::uuid
        OR tenant_id = '00000000-0000-0000-0000-000000000000'::uuid
    );

ALTER TABLE outbox_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_event FORCE ROW LEVEL SECURITY;

CREATE POLICY knowledge_outbox_tenant_isolation ON outbox_event
    USING (
        tenant_id = current_setting('app.tenant_id', true)::uuid
        OR tenant_id = '00000000-0000-0000-0000-000000000000'::uuid
    )
    WITH CHECK (
        tenant_id = current_setting('app.tenant_id', true)::uuid
        OR tenant_id = '00000000-0000-0000-0000-000000000000'::uuid
    );
