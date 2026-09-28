"""Stable conversation message ordering, including existing rows."""
from alembic import op
import sqlalchemy as sa

revision = '9bc7c261b302'
down_revision = '605e885948c8'
branch_labels = None
depends_on = None


def upgrade():
    op.add_column('messages', sa.Column('sequence', sa.Integer(), nullable=True))
    op.execute('''UPDATE messages AS message SET sequence = ordered.position
        FROM (SELECT id, row_number() OVER (PARTITION BY conversation_id ORDER BY created_at, id) - 1 AS position
              FROM messages) AS ordered WHERE message.id = ordered.id''')
    op.alter_column('messages', 'sequence', nullable=False)
    op.create_unique_constraint('uq_messages_conversation_id', 'messages', ['conversation_id', 'sequence'])


def downgrade():
    op.drop_constraint('uq_messages_conversation_id', 'messages', type_='unique')
    op.drop_column('messages', 'sequence')
