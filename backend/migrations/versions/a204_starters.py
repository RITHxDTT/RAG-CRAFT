"""Ordered chatbot starter questions."""
from alembic import op
import sqlalchemy as sa
revision='a204_starters'
down_revision='a203_catalog'
branch_labels=depends_on=None

def upgrade():
    op.add_column('chatbots', sa.Column('starter_questions', sa.JSON(), nullable=False, server_default='[]'))

def downgrade():
    op.drop_column('chatbots','starter_questions')
