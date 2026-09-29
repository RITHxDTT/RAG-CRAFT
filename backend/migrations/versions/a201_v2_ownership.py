"""Platform roles and explicit chatbot ownership; preserve existing tenants."""
from alembic import op
import sqlalchemy as sa
revision = 'a201_v2_ownership'
down_revision = '9bc7c261b302'
branch_labels = depends_on = None


def upgrade():
    op.add_column('users', sa.Column('full_name', sa.String(120), nullable=False, server_default=''))
    op.add_column('users', sa.Column('role', sa.String(20), nullable=False, server_default='USER'))
    op.execute("UPDATE users SET role='ADMIN' WHERE id IN (SELECT user_id FROM organization_members WHERE role='ADMIN')")
    op.create_check_constraint('ck_users_platform_role', 'users', "role IN ('ADMIN', 'USER')")
    op.add_column('chatbots', sa.Column('owner_id', sa.Uuid(), nullable=True))
    # Creator audit entries are authoritative when available; otherwise require one candidate.
    op.execute("""UPDATE chatbots b SET owner_id = (
        SELECT a.user_id FROM audit_logs a JOIN users u ON u.id=a.user_id
        WHERE a.resource_id=b.id AND a.action='chatbot.created'
        ORDER BY a.created_at, a.id LIMIT 1)""")
    op.execute("""UPDATE chatbots b SET owner_id=(SELECT m.user_id FROM organization_members m
        WHERE m.organization_id=b.organization_id AND m.role='ADMIN' LIMIT 1)
        WHERE owner_id IS NULL AND 1=(SELECT count(*) FROM organization_members m
        WHERE m.organization_id=b.organization_id AND m.role='ADMIN')""")
    op.execute("""DO $$ BEGIN IF EXISTS (SELECT 1 FROM chatbots WHERE owner_id IS NULL) THEN
        RAISE EXCEPTION 'Ambiguous legacy chatbot ownership. Supply verified chatbot.created audit records before retrying this migration.';
        END IF; END $$""")
    op.alter_column('chatbots', 'owner_id', nullable=False)
    op.create_foreign_key('fk_chatbots_owner_id_users', 'chatbots', 'users', ['owner_id'], ['id'], ondelete='RESTRICT')
    op.create_index('ix_chatbots_owner_id', 'chatbots', ['owner_id'])


def downgrade():
    op.drop_index('ix_chatbots_owner_id', 'chatbots')
    op.drop_constraint('fk_chatbots_owner_id_users', 'chatbots', type_='foreignkey')
    op.drop_column('chatbots', 'owner_id')
    op.drop_constraint('ck_users_platform_role', 'users', type_='check')
    op.drop_column('users', 'role')
    op.drop_column('users', 'full_name')
