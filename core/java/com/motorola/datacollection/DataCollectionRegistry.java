package com.motorola.datacollection;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import java.util.ArrayList;

public class DataCollectionRegistry
{
  private static final int ACTION_NEW_LOG = 0;
  private static final int MAX_BUFFER_SIZE = 5120;
  private static final String TAG = "DataCollectionRegistry";
  private Handler mHandler;
  private HandlerThread mNotifier = new HandlerThread("DataCollection Notifier");
  private final ArrayList<Record> mRecords = new ArrayList();
  private boolean mRegistered = false;
  private StringBuffer sb = new StringBuffer();
  
  public DataCollectionRegistry()
  {
    this.mNotifier.start();
    this.mHandler = new Handler(this.mNotifier.getLooper())
    {
      public void handleMessage(Message paramAnonymousMessage)
      {
        switch (paramAnonymousMessage.arg1)
        {
        default: 
          return;
        }
      }
    };
  }
  
  private void handleNotifyEventLog(String paramString)
  {
    synchronized (this.mRecords)
    {
      int i = this.mRecords.size() - 1;
      for (;;)
      {
        if (i >= 0)
        {
          Record localRecord = (Record)this.mRecords.get(i);
          try
          {
            localRecord.callback.onEventLog(paramString);
            i -= 1;
          }
          catch (RemoteException localRemoteException)
          {
            for (;;)
            {
              remove(localRecord.binder);
            }
          }
        }
      }
    }
  }
  
  private void remove(IBinder paramIBinder)
  {
    for (;;)
    {
      int i;
      synchronized (this.mRecords)
      {
        int j = this.mRecords.size();
        i = 0;
        if (i < j)
        {
          Record localRecord = (Record)this.mRecords.get(i);
          if (localRecord.binder == paramIBinder)
          {
            localRecord.binder.unlinkToDeath(localRecord.dw, 0);
            this.mRecords.remove(i);
          }
        }
        else
        {
          return;
        }
      }
      i += 1;
    }
  }
  
  public void listen(IDataCollectionListener listener, boolean paramBoolean)
  {
    if (paramBoolean) {}
    synchronized (this.mRecords)
    {
      IBinder localIBinder = listener.asBinder();
      int j = this.mRecords.size();
      int i = 0;
      Record localRecord = null;
      if (i < j) {}
      for (;;)
      {
        try
        {
          localRecord = (Record)this.mRecords.get(i);
          if (localIBinder == localRecord.binder) {
            return;
          }
          i += 1;
        }
        finally
        {
          continue;
        }
      }
    }
  }
  
  public void notifyEventLog(String paramString)
  {
    if ((!this.mRegistered) && (this.sb.length() + paramString.length() < 5120))
    {
      long l = System.currentTimeMillis();
      this.sb.append("time:" + l + ", " + paramString + "\n");
      return;
    }
    this.mHandler.obtainMessage(0, paramString).sendToTarget();
  }
  
  private class DeathWatcher
    implements IBinder.DeathRecipient
  {
    private final IBinder mBinder;
    
    DeathWatcher(IBinder paramIBinder)
    {
      this.mBinder = paramIBinder;
    }
    
    public void binderDied()
    {
      DataCollectionRegistry.this.remove(this.mBinder);
    }
  }
  
  private static class Record
  {
    IBinder binder;
    IDataCollectionListener callback;
    DataCollectionRegistry.DeathWatcher dw;
  }
}


/* Location:              /Users/Bryan/Desktop/usc/classes2-dex2jar.jar!/com/motorola/datacollection/DataCollectionRegistry.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       0.7.1
 */