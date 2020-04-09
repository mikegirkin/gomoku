#!/usr/bin/env python3

import os
import argparse
import pathlib
import psycopg2
import logging

logging.basicConfig(level=logging.INFO)

db_name = "gomoku"
db_host = "localhost"

def __db_connect():
    db_conn = psycopg2.connect(host=db_host, dbname=db_name)
    return db_conn


def __db_query(action):
    with __db_connect() as connection:
        with connection.cursor() as cursor:
            return action(connection, cursor)


def __drop_all_tables():
    def list_all_tables(connection, cursor):
        query = "select table_name from information_schema.tables where table_schema='public'"
        cursor.execute(query)
        table_list = cursor.fetchall()
        return map(lambda t: t[0], table_list)

    def drop_tables(table_list, connection, cursor):
        for table_name in table_list:
            logging.info("Droping table {}".format(table_name))
            query = "drop table if exists \"{}\" cascade".format(table_name)
            cursor.execute(query)
        connection.commit()

    connection = __db_connect()
    table_list = None
    with connection:
        with connection.cursor() as cursor:
            table_list = list_all_tables(connection, cursor)

        with connection.cursor() as cursor:
            drop_tables(table_list, connection, cursor)


def __get_project_directory() -> pathlib.PurePath:
    return pathlib.Path(__file__).parent.parent.resolve()


def __run_evolutions():
    evolutions_directory = __get_project_directory().joinpath("src/main/sql")
    logging.info("Runnning evolutions. Project directory: {}".format(evolutions_directory))
    evolutions = sorted(os.listdir(str(evolutions_directory)))
    for file_name in evolutions:
        logging.info("Running evolution {}".format(file_name))
        file_path = evolutions_directory.joinpath(file_name)
        result = os.system("psql -d {} -h {} -a -f {}".format(db_name, db_host, file_path))
        if result != 0:
            raise Exception("Failed when running evolutions on the database")


def drop_and_create_new():
    __drop_all_tables()
    __run_evolutions()


def main():
    drop_and_create_new()


if __name__ == "__main__":
    main()