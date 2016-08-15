package com.motorola.datacollection;

import android.os.Handler;
import android.os.Message;

public abstract class DataCollectionListener
{
  private static final int LISTEN_EVENT_LOG = 1;
  private static final int LISTEN_OLD_EVENT_LOG = 2;
  private static final String TAG = "DataCollectionListener";
  public IDataCollectionListener callback = new IDataCollectionListener.Stub()
  {
    public void onEventLog(String paramAnonymousString)
    {
      Message.obtain(DataCollectionListener.this.mHandler, 1, 0, 0, paramAnonymousString).sendToTarget();
    }
    
    public void onOldLogs(String paramAnonymousString)
    {
      Message.obtain(DataCollectionListener.this.mHandler, 2, 0, 0, paramAnonymousString).sendToTarget();
    }
  };
  Handler mHandler = new Handler()
  {
    public void handleMessage(Message paramAnonymousMessage)
    {
      switch (paramAnonymousMessage.what)
      {
      default: 
        return;
      case 1: 
        DataCollectionListener.this.onEventLog((String)paramAnonymousMessage.obj);
        return;
      }
    }
  };
  
  public abstract void onEventLog(String paramString);
  
  public abstract void onOldLogs(String paramString);
}