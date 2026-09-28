"""Run from the project root: python -m app.cli create-admin --email you@example.com (PYTHONPATH=backend)."""
import argparse
import getpass
import os
from pydantic import ValidationError
from sqlalchemy.orm import Session
from app.core.exceptions import AppError
from app.db.session import get_engine
from app.schemas.auth import AdminCreate
from app.services.auth_service import create_admin


def main():
    parser = argparse.ArgumentParser(description="RAG Craft administration")
    parser.add_argument("command", choices=["create-admin"])
    parser.add_argument("--email", required=True)
    parser.add_argument("--organization", default="Default Organization")
    args = parser.parse_args()
    password = os.environ.get("ADMIN_PASSWORD")
    if not password:
        password = getpass.getpass("Admin password (at least 12 characters): ")
        if password != getpass.getpass("Confirm password: "):
            parser.exit(1, "Passwords do not match.\n")
    try:
        data = AdminCreate(email=args.email, password=password, organization_name=args.organization)
        with Session(get_engine()) as db:
            create_admin(db, data)
    except ValidationError:
        parser.exit(1, "Use a valid email, a 12–256 character password, and a 1–120 character organization name.\n")
    except AppError as error:
        parser.exit(1, error.detail + "\n")
    print("Admin and default organization created.")


if __name__ == "__main__":
    main()
