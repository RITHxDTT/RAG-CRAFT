"""Preserve worksheet and row citation metadata."""
from alembic import op
import sqlalchemy as sa
revision='a205_xlsx_metadata'
down_revision='a204_starters'
branch_labels=depends_on=None

def upgrade():
    for table in ('document_chunks','message_sources'):
        op.add_column(table,sa.Column('sheet_name',sa.String(120),nullable=True))
        op.add_column(table,sa.Column('row_number',sa.Integer(),nullable=True))

def downgrade():
    for table in ('document_chunks','message_sources'):
        op.drop_column(table,'row_number')
        op.drop_column(table,'sheet_name')
