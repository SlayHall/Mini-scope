import network
import socket
import machine
import time

# Setup WiFi (Assuming Pico W)
wlan = network.WLAN(network.STA_IF)
wlan.active(True)
wlan.connect(ssid='Haitham', key='S@leMCool')

while not wlan.isconnected():
    pass

# Setup ADC
adc = machine.ADC(0)

# TCP Server Setup
addr = socket.getaddrinfo('192.168.1.101', 8080)[0][-1]
s = socket.socket()
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(addr)
s.listen(1)

# Buffer to store readings
buffer_size = 500
voltage_buffer = [0] * buffer_size
buffer_index = 0

while True:
    cl, addr = s.accept()
    print('Client connected from', addr)

    while True:
        try:
            start_time = time.ticks_us()  # Start time in microseconds
            for i in range(buffer_size):
                voltage = adc.read_u16() * (3.3 / 65535)  # 3.3V reference, 16-bit resolution
                voltage_buffer[buffer_index] = voltage
                buffer_index = (buffer_index + 1) % buffer_size

            # Send the buffer as a single packet
            data_string = ','.join(map(str, voltage_buffer)) + '\n'
            cl.send(bytes(data_string, 'utf-8'))

            # Calculate elapsed time and adjust delay to maintain sampling rate
            elapsed_time = time.ticks_diff(time.ticks_us(), start_time)
            delay_time = max(0, (1000000 // buffer_size) - elapsed_time)
            time.sleep_us(delay_time)
        except OSError:
            break
    cl.close()

