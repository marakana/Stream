/* $Id: $
   Copyright 2012, G. Blake Meike

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package com.marakana.android.stream.db.dao;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import com.marakana.android.stream.BuildConfig;
import com.marakana.android.stream.db.ColumnDef;
import com.marakana.android.stream.db.DbHelper;
import com.marakana.android.stream.db.StreamContract;
import com.marakana.android.stream.db.StreamProvider;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class TagsDao {
    private static final String TAG = "TAGS-DAO";

    private static final String TABLE = "tags";

    private static final String COL_ID = "id";
    private static final String COL_URI = "uri";
    private static final String COL_TITLE = "title";
    private static final String COL_DESC = "description";
    private static final String COL_TAGS_ICON = "_data";

    private static final String CREATE_TAGS_TABLE
        = "CREATE TABLE " + TABLE + " ("
            + COL_ID + " integer PRIMARY KEY AUTOINCREMENT,"
            + COL_URI + " text UNIQUE,"
            + COL_TITLE + " text,"
            + COL_DESC + " text,"
            + COL_TAGS_ICON + " text)";

    private static final String DROP_TAGS_TABLE
        = "DROP TABLE IF EXISTS " + TABLE;

    private static final String DEFAULT_SORT = StreamContract.Posts.Columns.PUB_DATE + " DESC";

    private static final String PK_CONSTRAINT = COL_ID + "=";

    private static final Map<String, ColumnDef> COL_MAP;
    static {
        Map<String, ColumnDef> m = new HashMap<String, ColumnDef>();
        m.put(
                StreamContract.Tags.Columns.ID,
                new ColumnDef(COL_ID, ColumnDef.Type.LONG));
        m.put(
                StreamContract.Tags.Columns.LINK,
                new ColumnDef(COL_URI, ColumnDef.Type.STRING));
        m.put(
                StreamContract.Tags.Columns.TITLE,
                new ColumnDef(COL_TITLE, ColumnDef.Type.STRING));
        m.put(
                StreamContract.Tags.Columns.DESC,
                new ColumnDef(COL_DESC, ColumnDef.Type.STRING));
        m.put(
                COL_TAGS_ICON,
                new ColumnDef(COL_TAGS_ICON, ColumnDef.Type.STRING));
        COL_MAP = Collections.unmodifiableMap(m);
    }

    private static final Map<String, String> COL_AS_MAP;
    static {
        Map<String, String> m = new HashMap<String, String>();
        m.put(
                StreamContract.Tags.Columns.ID,
                COL_ID + " AS " + StreamContract.Tags.Columns.ID);
        m.put(
                StreamContract.Tags.Columns.LINK,
                COL_URI + " AS " + StreamContract.Tags.Columns.LINK);
        m.put(
                StreamContract.Tags.Columns.TITLE,
                COL_TITLE + " AS " + StreamContract.Tags.Columns.TITLE);
        m.put(
                StreamContract.Tags.Columns.DESC,
                COL_DESC + " AS " + StreamContract.Tags.Columns.DESC);
        COL_AS_MAP = Collections.unmodifiableMap(m);
    }

    /**
     * @param context
     * @param db
     */
    public static void dropTable(Context context, SQLiteDatabase db) {
        if (BuildConfig.DEBUG) { Log.d(TAG, "drop tags db: " + DROP_TAGS_TABLE); }
        db.execSQL(DROP_TAGS_TABLE);
    }

    /**
     * @param context
     * @param db
     */
    public static void initDb(Context context, SQLiteDatabase db) {
        if (BuildConfig.DEBUG) { Log.d(TAG, "create tags db: " + CREATE_TAGS_TABLE); }
        db.execSQL(CREATE_TAGS_TABLE);
    }


    private final DbHelper dbHelper;
    private final StreamProvider provider;

    /**
     * @param provider
     * @param dbHelper
     */
    public TagsDao(StreamProvider provider, DbHelper dbHelper) {
        this.provider = provider;
        this.dbHelper = dbHelper;
    }

    /**
     * @param vals
     * @return pk for inserted row
     */
    public long insert(ContentValues vals) {
        if (BuildConfig.DEBUG) { Log.d(TAG, "insert tag: " + vals); }
        long pk = -1;
        vals = StreamProvider.translateCols(COL_MAP, vals);
        try {
            pk = dbHelper.getDb().insertWithOnConflict(
               TABLE,
               null,
               vals,
               SQLiteDatabase.CONFLICT_IGNORE);
        }
        catch (SQLException e) { Log.w(TAG, "insert failed: ", e); }
        return pk;
    }

    /**
     * @param proj
     * @param sel
     * @param selArgs
     * @param ord
     * @param pk
     * @return cursor
     */
    public Cursor query(String[] proj, String sel, String[] selArgs, String ord, long pk) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);

        qb.setProjectionMap(COL_AS_MAP);

        qb.setTables(TABLE);

        if (0 <= pk) { qb.appendWhere(PK_CONSTRAINT + pk); }

        if (TextUtils.isEmpty(ord)) { ord = DEFAULT_SORT; }

        return qb.query(dbHelper.getDb(), proj, sel, selArgs, null, null, ord);
    }

    /**
     * @param uri
     * @return descriptor for open file
     * @throws FileNotFoundException
     */
    public ParcelFileDescriptor openFile(Uri uri) throws FileNotFoundException {
        long pk = ContentUris.parseId(uri);
        if (0 > pk) { throw new IllegalArgumentException("Malformed URI: " + uri); }

        String fName = null;
        Cursor c = null;
        try {
            c = dbHelper.getDb().query(
                    TABLE,
                    new String[] { COL_TAGS_ICON },
                    PK_CONSTRAINT + pk,
                    null,
                    null,
                    null,
                    null);

            if (1 != c.getCount()) { throw new FileNotFoundException("No tag for: " + uri); }
            c.moveToFirst();

            fName = c.getString(c.getColumnIndex(COL_TAGS_ICON));
        }
        catch (Exception e) {
            Log.w(TAG, "WTF?", e);
        }
        finally {
            if (null != c) {
                try { c.close(); } catch (Exception e) { }
            }
        }

        if (BuildConfig.DEBUG) { Log.d(TAG, "Opening: " + fName); }
        ParcelFileDescriptor fd = null;
        try {
            fd = ParcelFileDescriptor.open(
                    new File(provider.getContext().getFilesDir(), fName),
                    ParcelFileDescriptor.MODE_READ_ONLY);
        }
        catch (Exception e) {
            throw new FileNotFoundException("Failed opening : " + fName);
        }

        return fd;
    }
}
