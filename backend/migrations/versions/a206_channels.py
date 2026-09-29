"""Channel integrations, guest conversation boundaries, and Telegram retry records."""
from alembic import op
import sqlalchemy as sa
revision='a206_channels'
down_revision='a205_xlsx_metadata'
branch_labels=depends_on=None

def record():
    return [sa.Column('id',sa.Uuid(),primary_key=True),sa.Column('created_at',sa.DateTime(timezone=True),nullable=False,server_default=sa.func.now()),sa.Column('updated_at',sa.DateTime(timezone=True),nullable=False,server_default=sa.func.now())]

def upgrade():
    op.create_table('channel_integrations',*record(),sa.Column('chatbot_id',sa.Uuid(),sa.ForeignKey('chatbots.id',ondelete='CASCADE'),nullable=False),
        sa.Column('channel',sa.String(20),nullable=False),sa.Column('public_id',sa.String(64),unique=True,nullable=False),
        sa.Column('enabled',sa.Boolean(),nullable=False),sa.Column('status',sa.String(20),nullable=False),sa.Column('credential',sa.Text()),sa.Column('webhook_secret',sa.String(64)),
        sa.Column('telegram_bot_id',sa.String(40),unique=True),sa.Column('telegram_username',sa.String(120)),
        sa.UniqueConstraint('chatbot_id','channel'),sa.CheckConstraint("channel IN ('PUBLIC_LINK','WEB_WIDGET','TELEGRAM')",name='ck_channel_integrations_channel'))
    op.create_index('ix_channel_integrations_chatbot_id','channel_integrations',['chatbot_id'])
    op.add_column('conversations',sa.Column('channel',sa.String(20),nullable=False,server_default='PLAYGROUND'))
    op.add_column('conversations',sa.Column('session_hash',sa.String(64)))
    op.add_column('conversations',sa.Column('integration_id',sa.Uuid(),sa.ForeignKey('channel_integrations.id',ondelete='SET NULL')))
    op.create_check_constraint('ck_conversations_channel','conversations',"channel IN ('PLAYGROUND','TELEGRAM','WEB_WIDGET','PUBLIC_LINK')")
    for field in ('channel','session_hash','integration_id'):
        op.create_index('ix_conversations_'+field,'conversations',[field])
    op.create_table('telegram_updates',*record(),sa.Column('integration_id',sa.Uuid(),sa.ForeignKey('channel_integrations.id',ondelete='CASCADE'),nullable=False),
        sa.Column('update_id',sa.BigInteger(),nullable=False),sa.Column('answer',sa.Text(),nullable=False),sa.Column('sent_parts',sa.Integer(),nullable=False),sa.UniqueConstraint('integration_id','update_id'))
    op.create_index('ix_telegram_updates_integration_id','telegram_updates',['integration_id'])

def downgrade():
    op.drop_table('telegram_updates')
    op.drop_constraint('ck_conversations_channel','conversations',type_='check')
    for field in ('channel','session_hash','integration_id'):
        op.drop_column('conversations',field)
    op.drop_table('channel_integrations')
