// SPDX-License-Identifier: AGPL-3.0-only
using System.Buffers.Binary;
using System.Collections.Concurrent;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Storage.Streams;

if (args.Length == 3 && args[0] == "discover")
{
    try
    {
        var profiles = args.Skip(1).Select(Guid.Parse).ToHashSet();
        var results = new ConcurrentDictionary<ulong, string>();
        using var scan = new CancellationTokenSource(TimeSpan.FromSeconds(4));
        await Watch(change =>
        {
            var name = (change.Advertisement.LocalName ?? "").Replace('\t', ' ').Replace('\n', ' ').Replace('\r', ' ');
            if (change.Advertisement.ServiceUuids.Any(profiles.Contains) || name.Length != 0)
                results.AddOrUpdate(change.BluetoothAddress, name, (_, existing) => name.Length == 0 ? existing : name);
        }, scan.Token);
        foreach (var result in results.OrderBy(entry => entry.Key))
            Console.WriteLine($"{result.Key:X12}\t{result.Value}");
        return 0;
    }
    catch (Exception error)
    {
        Console.Error.WriteLine(error.Message);
        return 1;
    }
}

if (args.Length != 7 || !ulong.TryParse(args[0], System.Globalization.NumberStyles.HexNumber,
        System.Globalization.CultureInfo.InvariantCulture, out var address))
    return 2;

using var cancelled = new CancellationTokenSource();
using var input = Console.OpenStandardInput();
using var output = Console.OpenStandardOutput();
var outputLock = new object();
try
{
    async Task<T> Wait<T>(Windows.Foundation.IAsyncOperation<T> operation) =>
        await operation.AsTask(cancelled.Token).WaitAsync(TimeSpan.FromSeconds(12), cancelled.Token);

    var observed = new TaskCompletionSource<BluetoothAddressType>(TaskCreationOptions.RunContinuationsAsynchronously);
    using (var scan = new CancellationTokenSource(TimeSpan.FromSeconds(3)))
    {
        await Watch(change =>
        {
            if (change.BluetoothAddress == address)
                observed.TrySetResult(change.BluetoothAddressType);
        }, scan.Token);
    }
    using var device = (observed.Task.IsCompletedSuccessfully
        ? await Wait(BluetoothLEDevice.FromBluetoothAddressAsync(address, observed.Task.Result))
        : await Wait(BluetoothLEDevice.FromBluetoothAddressAsync(address)))
        ?? await Wait(BluetoothLEDevice.FromBluetoothAddressAsync(address, BluetoothAddressType.Random))
        ?? throw new IOException("Bluetooth LE device unavailable");
    var services = await Wait(device.GetGattServicesAsync(BluetoothCacheMode.Uncached));
    if (services.Status != GattCommunicationStatus.Success)
        throw new IOException($"Bluetooth LE service lookup: {services.Status}");

    var opened = false;
    try
    {
        for (var index = 1; index < args.Length && !opened; index += 3)
        {
            var service = services.Services.FirstOrDefault(value => value.Uuid == Guid.Parse(args[index]));
            if (service is null) continue;
            var characteristics = await Wait(service.GetCharacteristicsAsync(BluetoothCacheMode.Uncached));
            if (characteristics.Status != GattCommunicationStatus.Success) continue;
            var tx = characteristics.Characteristics.FirstOrDefault(value => value.Uuid == Guid.Parse(args[index + 1]));
            var rx = characteristics.Characteristics.FirstOrDefault(value => value.Uuid == Guid.Parse(args[index + 2]));
            if (tx is null || rx is null) continue;
            var mode = tx.CharacteristicProperties.HasFlag(GattCharacteristicProperties.Write)
                ? GattWriteOption.WriteWithResponse : GattWriteOption.WriteWithoutResponse;
            if (mode == GattWriteOption.WriteWithoutResponse &&
                !tx.CharacteristicProperties.HasFlag(GattCharacteristicProperties.WriteWithoutResponse)) continue;
            var subscription = rx.CharacteristicProperties.HasFlag(GattCharacteristicProperties.Notify)
                ? GattClientCharacteristicConfigurationDescriptorValue.Notify
                : GattClientCharacteristicConfigurationDescriptorValue.Indicate;
            if (subscription == GattClientCharacteristicConfigurationDescriptorValue.Indicate &&
                !rx.CharacteristicProperties.HasFlag(GattCharacteristicProperties.Indicate)) continue;

            void OnValue(GattCharacteristic _, GattValueChangedEventArgs change)
            {
                try
                {
                    using var data = DataReader.FromBuffer(change.CharacteristicValue);
                    var bytes = new byte[data.UnconsumedBufferLength];
                    data.ReadBytes(bytes);
                    lock (outputLock) { output.Write(bytes); output.Flush(); }
                }
                catch (IOException) { cancelled.Cancel(); }
            }

            rx.ValueChanged += OnValue;
            try
            {
                if (await Wait(rx.WriteClientCharacteristicConfigurationDescriptorAsync(subscription))
                    != GattCommunicationStatus.Success) continue;
                opened = true;
                device.ConnectionStatusChanged += (_, _) =>
                {
                    if (device.ConnectionStatus == BluetoothConnectionStatus.Disconnected) cancelled.Cancel();
                };
                if (device.ConnectionStatus == BluetoothConnectionStatus.Disconnected)
                    throw new IOException("Bluetooth LE disconnected");
                Console.Error.WriteLine("READY");

                var length = new byte[4];
                while (!cancelled.IsCancellationRequested)
                {
                    await input.ReadExactlyAsync(length, cancelled.Token).AsTask().WaitAsync(cancelled.Token);
                    var size = BinaryPrimitives.ReadInt32BigEndian(length);
                    if (size is < 1 or > 4096) throw new IOException("Invalid Bluetooth write size");
                    var bytes = new byte[size];
                    await input.ReadExactlyAsync(bytes, cancelled.Token).AsTask().WaitAsync(cancelled.Token);
                    for (var offset = 0; offset < size;)
                    {
                        // ATT's minimum MTU leaves 20 bytes for characteristic data.
                        var count = Math.Min(20, size - offset);
                        using var writer = new DataWriter();
                        writer.WriteBytes(bytes.AsSpan(offset, count).ToArray());
                        var result = await Wait(tx.WriteValueAsync(writer.DetachBuffer(), mode));
                        if (result != GattCommunicationStatus.Success)
                            throw new IOException($"Bluetooth LE write: {result}");
                        offset += count;
                    }
                    Console.Error.WriteLine("OK");
                }
            }
            finally { rx.ValueChanged -= OnValue; }
        }
        if (!opened) throw new IOException("No compatible BLE KISS or Nordic UART service");
    }
    finally { foreach (var service in services.Services) service.Dispose(); }
    return 0;
}
catch (EndOfStreamException) { return 0; }
catch (Exception error)
{
    Console.Error.WriteLine($"ERROR {error.Message.Replace('\n', ' ').Replace('\r', ' ')}");
    return 1;
}

static async Task Watch(Action<BluetoothLEAdvertisementReceivedEventArgs> receive, CancellationToken stop)
{
    var watcher = new BluetoothLEAdvertisementWatcher { ScanningMode = BluetoothLEScanningMode.Active };
    watcher.Received += (_, change) => receive(change);
    watcher.Start();
    try { await Task.Delay(Timeout.Infinite, stop); }
    catch (OperationCanceledException) when (stop.IsCancellationRequested) { }
    finally { watcher.Stop(); }
}
