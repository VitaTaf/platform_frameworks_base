package com.motorola.datacollection;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public abstract interface IDataCollectionListener
  extends IInterface
{
  public abstract void onEventLog(String paramString)
    throws RemoteException;
  
  public abstract void onOldLogs(String paramString)
    throws RemoteException;
  
  public static abstract class Stub
    extends Binder
    implements IDataCollectionListener
  {
    private static final String DESCRIPTOR = "com.motorola.datacollection.IDataCollectionListener";
    static final int TRANSACTION_onEventLog = 1;
    static final int TRANSACTION_onOldLogs = 2;
    
    public Stub()
    {
      attachInterface(this, "com.motorola.datacollection.IDataCollectionListener");
    }
    
    public static IDataCollectionListener asInterface(IBinder paramIBinder)
    {
      if (paramIBinder == null) {
        return null;
      }
      IInterface localIInterface = paramIBinder.queryLocalInterface("com.motorola.datacollection.IDataCollectionListener");
      if ((localIInterface != null) && ((localIInterface instanceof IDataCollectionListener))) {
        return (IDataCollectionListener)localIInterface;
      }
      return new Proxy(paramIBinder);
    }
    
    public IBinder asBinder()
    {
      return this;
    }
    
    public boolean onTransact(int paramInt1, Parcel paramParcel1, Parcel paramParcel2, int paramInt2)
      throws RemoteException
    {
      switch (paramInt1)
      {
      default: 
        return super.onTransact(paramInt1, paramParcel1, paramParcel2, paramInt2);
      case 1598968902: 
        paramParcel2.writeString("com.motorola.datacollection.IDataCollectionListener");
        return true;
      case 1: 
        paramParcel1.enforceInterface("com.motorola.datacollection.IDataCollectionListener");
        onEventLog(paramParcel1.readString());
        return true;
      }
    }
    
    private static class Proxy
      implements IDataCollectionListener
    {
      private IBinder mRemote;
      
      Proxy(IBinder paramIBinder)
      {
        this.mRemote = paramIBinder;
      }
      
      public IBinder asBinder()
      {
        return this.mRemote;
      }
      
      public String getInterfaceDescriptor()
      {
        return "com.motorola.datacollection.IDataCollectionListener";
      }
      
      public void onEventLog(String paramString)
        throws RemoteException
      {
        Parcel localParcel = Parcel.obtain();
        try
        {
          localParcel.writeInterfaceToken("com.motorola.datacollection.IDataCollectionListener");
          localParcel.writeString(paramString);
          this.mRemote.transact(1, localParcel, null, 1);
          return;
        }
        finally
        {
          localParcel.recycle();
        }
      }
      
      public void onOldLogs(String paramString)
        throws RemoteException
      {
        Parcel localParcel = Parcel.obtain();
        try
        {
          localParcel.writeInterfaceToken("com.motorola.datacollection.IDataCollectionListener");
          localParcel.writeString(paramString);
          this.mRemote.transact(2, localParcel, null, 1);
          return;
        }
        finally
        {
          localParcel.recycle();
        }
      }
    }
  }
}


/* Location:              /Users/Bryan/Desktop/usc/classes2-dex2jar.jar!/com/motorola/datacollection/IDataCollectionListener.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       0.7.1
 */