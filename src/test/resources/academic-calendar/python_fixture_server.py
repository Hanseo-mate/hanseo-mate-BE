"""Opt-in contract fixture. No production DB or school HTTP is permitted.

Loads the supplied crawler source unchanged, substitutes its DB configuration
before database imports, and runs actual API/service/repository code on SQLite.
"""
import argparse
from datetime import date, datetime
import json
from pathlib import Path
import shutil
import socket
import sys
import unittest

parser = argparse.ArgumentParser()
parser.add_argument("source")
parser.add_argument("scratch")
parser.add_argument("--unit", action="store_true")
args = parser.parse_args()
source = Path(args.source).resolve()
scratch = Path(args.scratch).resolve()
scratch.mkdir(parents=True, exist_ok=True)
sys.dont_write_bytecode = True
sys.path.insert(0, str(source))

import crawler.config as config
config.DB_CONFIG = dict(host="127.0.0.1", user="isolated", password="isolated",
                        database="unused_contract_test", charset="utf8mb4")

import pymysql
from sqlalchemy import create_engine, event, select
from sqlalchemy.engine import Engine
from sqlalchemy.orm import sessionmaker

def no_mysql(*a, **kw):
    raise AssertionError("MySQL access is forbidden in this isolated fixture")
pymysql.connect = no_mysql

@event.listens_for(Engine, "before_cursor_execute")
def sqlite_only(connection, *a):
    if connection.dialect.name != "sqlite":
        raise AssertionError("Only isolated SQLite may execute SQL")

if args.unit:
    import os
    os.environ.pop("ACADEMIC_CALENDAR_TEST_MYSQL_URL", None)
    copied = scratch / "tests"
    shutil.copytree(source / "tests", copied, dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns("__pycache__", ".calendar-test-*"))
    suite = unittest.defaultTestLoader.discover(str(copied))
    result = unittest.TextTestRunner(verbosity=1).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)

import crawler.api as api
from crawler.academic_calendar.migrate import migrate
from crawler.academic_calendar.models import CalendarBase, SchoolCalendarEvent
from crawler.academic_calendar.service import AcademicCalendarCrawlService
import uvicorn

engine = create_engine("sqlite:///" + str(scratch / "calendar.sqlite"),
                       connect_args={"check_same_thread": False})
CalendarBase.metadata.create_all(engine)  # Test-only; not a MySQL migration check.
migrate(engine)
sessions = sessionmaker(bind=engine, expire_on_commit=False)
with sessions() as session, session.begin():
    now = datetime(2026, 1, 1)
    for source_name, event_id, title in [
            ("MANUAL", None, "manual preserved"),
            ("HANSEO", "obsolete-other", "obsolete other")]:
        session.add(SchoolCalendarEvent(start_date=date(2026, 4, 1), end_date=date(2026, 4, 1),
                    title=title, source=source_name, source_event_id=event_id,
                    academic_year=2026 if source_name == "HANSEO" else None,
                    semester=1 if source_name == "HANSEO" else None, event_type="OTHER",
                    active=True, created_at=now, updated_at=now))

def fetch_fixture(url):
    from urllib.parse import parse_qs, urlparse
    query = parse_qs(urlparse(url).query)
    assert query["schdYear"] == ["2026"]
    semester = int(query["section"][0])
    assert semester in (1, 2)
    return (source / "tests" / "fixtures" / "academic_calendar" /
            f"2026_{semester}.html").read_text(encoding="utf-8")

api.academic_calendar_crawl_service = AcademicCalendarCrawlService(sessions, fetch_fixture)

# Fail closed if a future code path accidentally attempts a real school request.
import requests
def no_school(*a, **kw):
    raise AssertionError("School HTTP is forbidden; use stored fixtures")
requests.sessions.Session.request = no_school

@api.app.get("/__test__/rows")
def rows():
    with sessions() as session:
        return [{column.name: getattr(row, column.name)
                 for column in SchoolCalendarEvent.__table__.columns}
                for row in session.scalars(select(SchoolCalendarEvent).order_by(SchoolCalendarEvent.id))]

sock = socket.socket()
sock.bind(("127.0.0.1", 0))
sock.listen(128)
(scratch / "port.txt").write_text(str(sock.getsockname()[1]), encoding="utf-8")
uvicorn.Server(uvicorn.Config(api.app, log_level="warning", lifespan="off")).run(sockets=[sock])
