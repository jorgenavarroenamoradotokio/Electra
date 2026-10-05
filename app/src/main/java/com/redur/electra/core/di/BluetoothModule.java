package com.redur.electra.core.di;

import com.redur.electra.data.bluetooth.AndroidBluetoothPrinterGateway;
import com.redur.electra.data.bluetooth.BluetoothPrinterGateway;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public abstract class BluetoothModule {

    @Binds
    abstract BluetoothPrinterGateway bindBluetoothPrinterGateway(AndroidBluetoothPrinterGateway gateway);
}
