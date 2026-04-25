#include <stdarg.h>
#include <string.h>

struct Parse {
    char *zDb;
};

struct Table {
    char *zName;
    int regRoot;
    int regRowid;
};

static int verify(const char *fmt, va_list ap)
{
    char *db = va_arg(ap, char *);
    char *type = va_arg(ap, char *);
    char *name = va_arg(ap, char *);
    char *tbl = va_arg(ap, char *);
    int root = va_arg(ap, int);
    char *sql = va_arg(ap, char *);
    int rowid = va_arg(ap, int);

    (void)fmt;
    if (strcmp(db, "main") != 0) return 1;
    if (strcmp(type, "table") != 0) return 2;
    if (strcmp(name, "foo") != 0) return 3;
    if (strcmp(tbl, "foo") != 0) return 4;
    if (root != 2) return 5;
    if (strcmp(sql, "CREATE TABLE foo(i int)") != 0) return 6;
    if (rowid != 10) return 7;
    return 0;
}

static int nested(struct Parse *p, const char *fmt, ...)
{
    va_list ap;
    int rc;
    (void)p;
    va_start(ap, fmt);
    rc = verify(fmt, ap);
    va_end(ap);
    return rc;
}

static int endTable(struct Parse *p, struct Table *t, char *zType, char *zStmt)
{
    return nested(p,
            "UPDATE %Q.sqlite_master"
            " SET type='%s', name=%Q, tbl_name=%Q, rootpage=#%d, sql=%Q"
            " WHERE rowid=#%d",
            p->zDb,
            zType,
            t->zName,
            t->zName,
            t->regRoot,
            zStmt,
            t->regRowid);
}

int main(void)
{
    struct Parse p = { "main" };
    struct Table t = { "foo", 2, 10 };
    return endTable(&p, &t, "table", "CREATE TABLE foo(i int)");
}
