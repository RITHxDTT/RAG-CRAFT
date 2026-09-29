"""Single-use hashed password reset tokens."""
from alembic import op
import sqlalchemy as sa
revision = 'a202_password_reset'
down_revision = 'a201_v2_ownership'
branch_labels = depends_on = None

def upgrade():
    op.create_table('password_resets',
        sa.Column('id', sa.Uuid(), primary_key=True),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column('user_id', sa.Uuid(), sa.ForeignKey('users.id', ondelete='CASCADE'), nullable=False),
        sa.Column('token_hash', sa.String(64), unique=True, nullable=False),
        sa.Column('expires_at', sa.DateTime(timezone=True), nullable=False))
    op.create_index('ix_password_resets_user_id', 'password_resets', ['user_id'])

def downgrade():
    op.drop_table('password_resets')
