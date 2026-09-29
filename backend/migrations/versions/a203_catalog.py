"""Platform model catalog and reusable prompt templates."""
from alembic import op
import sqlalchemy as sa
from uuid import uuid4
from app.core.config import get_settings
revision = 'a203_catalog'
down_revision = 'a202_password_reset'
branch_labels = depends_on = None

def timestamps():
    return [sa.Column('id', sa.Uuid(), primary_key=True), sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()), sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now())]

def upgrade():
    op.create_table('models', *timestamps(), sa.Column('name', sa.String(120), nullable=False),
        sa.Column('provider', sa.String(20), nullable=False), sa.Column('model_identifier', sa.String(120), unique=True, nullable=False),
        sa.Column('enabled', sa.Boolean(), nullable=False), sa.Column('is_default', sa.Boolean(), nullable=False),
        sa.CheckConstraint("provider = 'OLLAMA'", name='ck_models_provider'), sa.CheckConstraint('NOT is_default OR enabled', name='ck_models_default_enabled'))
    op.create_index('uq_models_default', 'models', ['is_default'], unique=True, postgresql_where=sa.text('is_default'))
    op.create_table('system_prompt_templates', *timestamps(), sa.Column('name', sa.String(120), nullable=False),
        sa.Column('prompt', sa.Text(), nullable=False), sa.Column('enabled', sa.Boolean(), nullable=False))
    op.add_column('chatbot_settings', sa.Column('model_id', sa.Uuid(), nullable=True))
    op.add_column('chatbot_settings', sa.Column('prompt_template_id', sa.Uuid(), nullable=True))
    op.add_column('chatbot_settings', sa.Column('tone', sa.String(20), nullable=False, server_default='PROFESSIONAL'))
    op.add_column('chatbot_settings', sa.Column('custom_instruction', sa.Text(), nullable=False, server_default=''))
    op.create_foreign_key('fk_chatbot_settings_model_id_models', 'chatbot_settings', 'models', ['model_id'], ['id'], ondelete='RESTRICT')
    op.create_foreign_key('fk_chatbot_settings_prompt_template_id_system_prompt_templates', 'chatbot_settings', 'system_prompt_templates', ['prompt_template_id'], ['id'], ondelete='SET NULL')
    for field in ('model_id', 'prompt_template_id'):
        op.create_index('ix_chatbot_settings_'+field, 'chatbot_settings', [field])
    model_name = get_settings().ollama_model
    op.execute(sa.text("INSERT INTO models (id, name, provider, model_identifier, enabled, is_default) VALUES (:id, :name, 'OLLAMA', :name, true, true)").bindparams(id=uuid4(), name=model_name))
    op.execute("""INSERT INTO models (id, name, provider, model_identifier, enabled, is_default)
        SELECT gen_random_uuid(), model_name, 'OLLAMA', model_name, true, false FROM chatbot_settings
        WHERE model_name NOT IN (SELECT model_identifier FROM models) GROUP BY model_name""")
    op.execute('UPDATE chatbot_settings s SET model_id=m.id FROM models m WHERE m.model_identifier=s.model_name')

def downgrade():
    for field in ('model_id', 'prompt_template_id', 'tone', 'custom_instruction'):
        op.drop_column('chatbot_settings', field)
    op.drop_table('system_prompt_templates')
    op.drop_table('models')
