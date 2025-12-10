#!/usr/bin/env python3 

""" 
RD-03D Radar BLE GATT Server for Android App Integration 
Using bluez DBus interface for Raspberry Pi 

Requirements: 
    sudo apt-get install python3-dbus python3-gi python3-gi-cairo gir1.2-gtk-3.0 
    pip3 install pydbus pyserial numpy 

Run with: 
    sudo python3 rd03d_ble_gatt_server.py --port /dev/ttyUSB0 
""" 
import argparse 
import struct 
import time 
import math 
import queue 
import threading 
import logging 
import array 
from typing import Dict, List, Optional 
import dbus 
import dbus.mainloop.glib 
import dbus.service 
import serial 
import numpy as np 
from gi.repository import GLib 

# Import radar constants 
FRAME_HEADER = b"\xAA\xFF\x03\x00" 
FRAME_TAIL = b"\x55\xCC" 
FRAME_LEN = 30 
TARGETS_PER_FRAME = 3 

# Position scaling for visualization (screen exaggeration) 
POSITION_SCALE = 3.0  # try 3.0–5.0; increase if you want more motion 
MAX_POSITION_METERS = 10.0  # clamp to your app's grid range (-10..10) 

# BLE UUIDs matching Android app 
SERVICE_UUID = "00001101-0000-1000-8000-00805f9b34fb" 
CHAR_RADAR_DATA_UUID = "00002a01-0000-1000-8000-00805f9b34fb" 
CHAR_COMMAND_UUID = "00002a02-0000-1000-8000-00805f9b34fb" 

# DBus constants 
DBUS_OM_IFACE = 'org.freedesktop.DBus.ObjectManager' 
DBUS_PROP_IFACE = 'org.freedesktop.DBus.Properties' 
BLUEZ_SERVICE_NAME = 'org.bluez' 
GATT_SERVICE_IFACE = 'org.bluez.GattService1' 
GATT_CHRC_IFACE = 'org.bluez.GattCharacteristic1' 
GATT_DESC_IFACE = 'org.bluez.GattDescriptor1' 
LE_ADVERTISING_MANAGER_IFACE = 'org.bluez.LEAdvertisingManager1' 
LE_ADVERTISEMENT_IFACE = 'org.bluez.LEAdvertisement1' 
GATT_MANAGER_IFACE = 'org.bluez.GattManager1' 
ADAPTER_IFACE = 'org.bluez.Adapter1' 

# Object type enum 
OBJECT_TYPE_UNKNOWN = 0 
OBJECT_TYPE_PERSON = 1 
OBJECT_TYPE_OBJECT = 2 
OBJECT_TYPE_GHOST = 3 

class RD03DRadar: 
    """Reader/parser for RD-03D radar""" 
    def __init__(self, port: str, baudrate: int = 256000, timeout: float = 0.05): 
        self.ser = serial.Serial( 
            port=port, 
            baudrate=baudrate, 
            bytesize=serial.EIGHTBITS, 
            parity=serial.PARITY_NONE, 
            stopbits=serial.STOPBITS_ONE, 
            timeout=timeout, 
        ) 
        self.buffer = bytearray() 
        logging.info(f"Radar initialized on {port} at {baudrate} baud") 

    def close(self): 
        if self.ser and self.ser.is_open: 
            self.ser.close() 

    @staticmethod 
    def _decode_signed_mag(u16: int) -> int: 
        if u16 == 0: 
            return 0 
        sign_positive = bool(u16 & 0x8000) 
        mag = u16 & 0x7FFF 
        return mag if sign_positive else -mag 

    def _extract_frame_from_buffer(self): 
        while True:
            idx = self.buffer.find(FRAME_HEADER) 
            if idx < 0: 
                if len(self.buffer) > 3: 
                    self.buffer = self.buffer[-3:] 
                return None 
 
            if len(self.buffer) - idx < FRAME_LEN: 
                return None 
 
            frame = self.buffer[idx: idx + FRAME_LEN] 
            del self.buffer[: idx + FRAME_LEN] 
 
            if frame[-2:] != FRAME_TAIL: 
                continue
 
            return frame 
 
    def read_frame(self): 
        try: 
            chunk = self.ser.read(64) 
        except serial.SerialException as e: 
            logging.error(f"Serial read error: {e}") 
            time.sleep(0.1) 
            return None 
 
        if chunk: 
            self.buffer.extend(chunk) 
        return self._extract_frame_from_buffer() 
 
    def parse_targets(self, frame: bytes): 
        assert len(frame) == FRAME_LEN 
 
        targets = [] 
        offset = 4 
 
        for idx in range(TARGETS_PER_FRAME): 
            segment = frame[offset: offset + 8] 
            offset += 8 
 
            if segment == b"\x00" * 8: 
                continue 
 
            x_raw, y_raw, v_raw, d_raw = struct.unpack_from("<HHHH", segment, 0) 
 
            x_mm = self._decode_signed_mag(x_raw) 
            y_mm = self._decode_signed_mag(y_raw) 
            speed_cm_s = self._decode_signed_mag(v_raw) 
            dist_gate_mm = d_raw 
 
            r_mm = int(math.hypot(x_mm, y_mm)) 
 
            targets.append({ 
                "id": idx + 1, 
                "x_mm": x_mm, 
                "y_mm": y_mm, 
                "r_mm": r_mm, 
                "speed_cm_s": speed_cm_s, 
                "dist_gate_mm": dist_gate_mm, 
            }) 
        return targets 

class Advertisement(dbus.service.Object): 
    PATH_BASE = '/org/bluez/rd03d/advertisement' 
 
    def __init__(self, bus, index, advertising_type): 
        self.path = self.PATH_BASE + str(index) 
        self.bus = bus 
        self.ad_type = advertising_type 
        # Broadcast as the name your Android app expects 
        self.local_name = 'PULSE_Radar' 
        self.service_uuids = [SERVICE_UUID] 
        # Include local name and tx-power in the advertising payload 
        self.includes = ['tx-power'] 
        dbus.service.Object.__init__(self, bus, self.path) 
 
    def get_properties(self): 
        properties = dict() 
        properties['Type'] = self.ad_type 
        properties['LocalName'] = dbus.String(self.local_name) 
        properties['ServiceUUIDs'] = dbus.Array(self.service_uuids, signature='s') 
        properties['Includes'] = dbus.Array(self.includes, signature='s') 
        return {LE_ADVERTISEMENT_IFACE: properties} 
 
    def get_path(self): 
        return dbus.ObjectPath(self.path) 
 
    @dbus.service.method(DBUS_PROP_IFACE, in_signature='ss', out_signature='v') 
    def Get(self, interface, prop): 
        return self.GetAll(interface)[prop] 
 
    @dbus.service.method(DBUS_PROP_IFACE, in_signature='s', out_signature='a{sv}') 
    def GetAll(self, interface): 
        return self.get_properties()[interface] 
 
    @dbus.service.method(LE_ADVERTISEMENT_IFACE, in_signature='', out_signature='') 
    def Release(self): 
        logging.info('Advertisement released') 
 
 
class Service(dbus.service.Object): 
    PATH_BASE = '/org/bluez/rd03d/service' 
 
    def __init__(self, bus, index, uuid, primary): 
        self.path = self.PATH_BASE + str(index) 
        self.bus = bus 
        self.uuid = uuid 
        self.primary = primary 
        self.characteristics: List[Characteristic] = [] 
        dbus.service.Object.__init__(self, bus, self.path) 
 
    def get_properties(self): 
        return {

            GATT_SERVICE_IFACE: { 
                'UUID': self.uuid, 
                'Primary': self.primary, 
                'Characteristics': dbus.Array( 
                    self.get_characteristic_paths(), signature='o' 
                ) 
            } 
        } 
 
    def get_path(self): 
        return dbus.ObjectPath(self.path) 
 
    def get_characteristic_paths(self): 
        return [chrc.get_path() for chrc in self.characteristics] 
 
    def add_characteristic(self, characteristic): 
        self.characteristics.append(characteristic) 
 
    @dbus.service.method(DBUS_PROP_IFACE, in_signature='ss', out_signature='v') 
    def Get(self, interface, prop): 
        return self.GetAll(interface)[prop] 
 
    @dbus.service.method(DBUS_PROP_IFACE, in_signature='s', out_signature='a{sv}') 
    def GetAll(self, interface): 
        return self.get_properties()[interface] 
 
    @dbus.service.method(DBUS_OM_IFACE, out_signature='a{oa{sa{sv}}}') 
    def GetManagedObjects(self): 
        response = {} 
        response[self.get_path()] = self.get_properties() 
        for chrc in self.characteristics: 
            response[chrc.get_path()] = chrc.get_properties() 
        return response 
 
 
class Characteristic(dbus.service.Object): 
    def __init__(self, bus, index, uuid, flags, service): 
        self.path = service.path + '/char' + str(index) 
        self.bus = bus 
        self.uuid = uuid 
        self.service = service 
        self.flags = flags 
        self.notifying = False 
        self.value: List[dbus.Byte] = [] 
        dbus.service.Object.__init__(self, bus, self.path) 
        service.add_characteristic(self) 
 
    def get_properties(self): 
        return { 
            GATT_CHRC_IFACE: { 
                'Service': self.service.get_path(), 
                'UUID': self.uuid, 
                'Flags': self.flags, 
                'Value': dbus.Array(self.value, signature='y'), 
                'Notifying': self.notifying 
            } 
        } 
 
    def get_path(self): 
        return dbus.ObjectPath(self.path) 
 
    @dbus.service.method(DBUS_PROP_IFACE, in_signature='ss', out_signature='v') 
    def Get(self, interface, prop): 
        return self.GetAll(interface)[prop] 
 
    @dbus.service.method(DBUS_PROP_IFACE, in_signature='s', out_signature='a{sv}') 
    def GetAll(self, interface): 
        return self.get_properties()[interface] 
 
    @dbus.service.signal(DBUS_PROP_IFACE, signature='sa{sv}as') 
    def PropertiesChanged(self, interface, changed, invalidated): 
        pass 
 
    # BlueZ ReadValue(dict options) -> ay 
    @dbus.service.method(GATT_CHRC_IFACE, in_signature='a{sv}', out_signature='ay') 
    def ReadValue(self, otions): 

        return self.value 
 
    # BlueZ WriteValue(ay value, dict options) -> () 
    @dbus.service.method(GATT_CHRC_IFACE, in_signature='aya{sv}', out_signature='') 
    def WriteValue(self, value, options): 
        self.value = value 
        if self.uuid == CHAR_COMMAND_UUID: 
            self.handle_command(value) 
 
    @dbus.service.method(GATT_CHRC_IFACE, in_signature='', out_signature='') 
    def StartNotify(self): 
        if self.notifying: 
            return 
        self.notifying = True 
        logging.info(f'Notifications enabled for {self.uuid}') 
 
    @dbus.service.method(GATT_CHRC_IFACE, in_signature='', out_signature='') 
    def StopNotify(self): 
        if not self.notifying: 
            return 
        self.notifying = False 
        logging.info(f'Notifications disabled for {self.uuid}') 
 
    def update_value(self, value): 
        self.value = value 
        if self.notifying: 
            self.PropertiesChanged( 
                GATT_CHRC_IFACE, 
                {'Value': dbus.Array(value, signature='y')}, 
                [] 
            ) 
 
    def handle_command(self, data): 
        """Override in subclass"""
        pass 
 
 
class RadarDataCharacteristic(Characteristic): 
    def __init__(self, bus, index, service): 
        # Only notify from this characteristic 
        Characteristic.__init__( 
            self, bus, index, CHAR_RADAR_DATA_UUID, 
            ['notify'], service 
        ) 
        self.radar_queue: Optional[queue.Queue] = None 
 
        # These are kept for potential future use (object tracking), 
        # but not used in the simplified packet format. 
        self.object_tracker = {} 
        self.next_object_id = 1 
        self.base_timestamp = int(time.time() * 1000) 
 
        # Start update loop at ~20 Hz 
        GLib.timeout_add(50, self.update_radar_data) 
 
    def set_radar_queue(self, queue_obj: queue.Queue): 
        self.radar_queue = queue_obj 
 
    def classify_target(self, target: Dict) -> tuple: 
        speed_m_s = abs(target["speed_cm_s"]) / 100.0 
        r_m = target["r_mm"] / 1000.0 
 
        if r_m < 0.5 or r_m > 8.0: 
            return OBJECT_TYPE_GHOST, 0.2 
        elif speed_m_s > 4.0: 
            return OBJECT_TYPE_GHOST, 0.3 
        elif speed_m_s < 0.05: 
            return OBJECT_TYPE_OBJECT, 0.8 
        else: 
            return OBJECT_TYPE_PERSON, 0.85 
 
    def pack_radar_data(self, target: Dict) -> bytes: 
        """ 
        Pack one detection in a format aligned with SimulatedRadarService semantics: 
 
        xPosition: Float (meters, VISUALIZED – scaled & clamped) 
        yPosition: Float (meters, VISUALIZED – scaled & clamped) 
        depth:     Float (meters, true radial distance) 
        snr:       Float (dB, ~5–35) 
        confidence:Float (0–1, derived from SNR) 
        objectType:Byte  (enum-like: 0/1/2/3) 
 
        Layout: <fffffB (little-endian) 
        """ 
 
        # True physical positions (meters) 
        x_m = target["x_mm"] / 1000.0 
        y_m = target["y_mm"] / 1000.0 
        depth_m = target["r_mm"] / 1000.0  # physical distance from sensor 
 
        # --- VISUAL SCALING for screen: exaggerate lateral movement --- 
        vis_x = x_m * POSITION_SCALE 
        vis_y = y_m * POSITION_SCALE 
 
        # Clamp to app grid range, e.g. -10..10 meters 
        vis_x = max(-MAX_POSITION_METERS, min(MAX_POSITION_METERS, vis_x)) 
        vis_y = max(-MAX_POSITION_METERS, min(MAX_POSITION_METERS, vis_y)) 
 
        # Approximate SNR based on range and clamp to 5–35 dB 
        snr_db = 40.0 - depth_m * 5.0 
        if snr_db < 5.0: 
            snr_db = 5.0 
        elif snr_db > 35.0: 
            snr_db = 35.0 
 
        # Confidence: ((snr - 5) / 30).coerceIn(0f, 1f) 
        confidence = (snr_db - 5.0) / 30.0 
        if confidence < 0.0: 
            confidence = 0.0 
        elif confidence > 1.0: 
            confidence = 1.0 
 
        # Use classifier for object type 
        object_type, _ = self.classify_target(target) 
        object_type_byte = int(object_type) & 0xFF 
 
        # Pack as: x, y, depth, snr, confidence, objectType 
        packet = struct.pack( 
            '<fffffB', 
            float(vis_x),      # scaled x 
            float(vis_y),      # scaled y 
            float(depth_m),    # true depth 
            float(snr_db),

            float(confidence), 
            object_type_byte 
        ) 
 
        return packet 
 
 
    def update_radar_data(self): 
        if not self.notifying or not self.radar_queue: 
            return True 
 
        targets = None 
        try: 
            while not self.radar_queue.empty(): 
                targets = self.radar_queue.get_nowait() 
        except queue.Empty: 
            targets = None 
 
        if targets: 
            # Choose closest target 
            sorted_targets = sorted(targets, key=lambda t: t["r_mm"]) 
            if sorted_targets: 
                target = sorted_targets[0] 
                packet = self.pack_radar_data(target) 
 
                value = [dbus.Byte(b) for b in packet] 
                self.update_value(value) 
 
                logging.debug( 
                    "Sent BLE detection: " 
                    "x=%.2fm, y=%.2fm, depth=%.2fm, snr=%.1fdB", 
                    target["x_mm"] / 1000.0, 
                    target["y_mm"] / 1000.0, 
                    target["r_mm"] / 1000.0, 
                    40.0 - (target["r_mm"] / 1000.0) * 5.0 
                ) 
 
        return True  # continue timeout 
 
 
class CommandCharacteristic(Characteristic): 
    def __init__(self, bus, index, service, radar_char: RadarDataCharacteristic): 
        Characteristic.__init__( 
            self, bus, index, CHAR_COMMAND_UUID, 
            ['write', 'write-without-response'], service 
        ) 
        self.radar_char = radar_char 
 
    def handle_command(self, data): 
        # data is a list of dbus.Byte 
        raw = bytes(data) 
        if len(raw) == 0: 
            return 
 
        cmd = raw[0] 
        if cmd == 0x01:  # Reset tracking 
            self.radar_char.object_tracker.clear() 
            self.radar_char.next_object_id = 1 
            logging.info("Reset object tracking") 
        elif cmd == 0x02:  # Reset timestamp 
            self.radar_char.base_timestamp = int(time.time() * 1000) 
            logging.info("Reset timestamp base") 
 
 
class RadarGattServer: 
    def __init__(self, adapter_name='hci0'): 
        self.adapter_name = adapter_name 
        self.mainloop = None 
        self.app = None 
        self.adv = None 
 
        # Get DBus 
        dbus.mainloop.glib.DBusGMainLoop(set_as_default=True) 
        self.bus = dbus.SystemBus() 
 
        # Get adapter 
        self.adapter = self.find_adapter() 
        if not self.adapter: 
            raise Exception('Bluetooth adapter not found') 
 
        # Power on adapter 
        self.power_on_adapter() 
 
    def find_adapter(self): 
        remote_om = dbus.Interface( 
            self.bus.get_object(BLUEZ_SERVICE_NAME, '/'), 
            DBUS_OM_IFACE 
        ) 
        objects = remote_om.GetManagedObjects() 
 
        for o, props in objects.items(): 
            if ADAPTER_IFACE in props: 
                if props[ADAPTER_IFACE].get('Address'):
                    return o 
        return None 
 
    def power_on_adapter(self): 
        adapter_props = dbus.Interface( 
            self.bus.get_object(BLUEZ_SERVICE_NAME, self.adapter), 
            DBUS_PROP_IFACE 
        ) 
        adapter_props.Set(ADAPTER_IFACE, 'Powered', dbus.Boolean(1)) 
        logging.info("Adapter powered on") 
 
    def register_app(self, radar_queue: queue.Queue): 
        # Create service 
        self.app = Service(self.bus, 0, SERVICE_UUID, True) 
 
        # Create characteristics 
        radar_char = RadarDataCharacteristic(self.bus, 0, self.app) 
        radar_char.set_radar_queue(radar_queue) 
 
        CommandCharacteristic(self.bus, 1, self.app, radar_char) 
 
        # Register application 
        manager = dbus.Interface( 
            self.bus.get_object(BLUEZ_SERVICE_NAME, self.adapter), 
            GATT_MANAGER_IFACE 
        ) 
 
        manager.RegisterApplication( 
            self.app.get_path(), {}, 
            reply_handler=self.register_app_cb, 
            error_handler=self.register_app_error_cb 
        ) 

    def register_app_cb(self):        
      logging.info('GATT application registered') 

    def register_app_error_cb(self, error): 
        logging.error(f'Failed to register application: {str(error)}') 
        if self.mainloop: 
            self.mainloop.quit() 
 
    def register_ad(self): 
        # Create advertisement 
        self.adv = Advertisement(self.bus, 0, 'peripheral') 
 
        manager = dbus.Interface( 
            self.bus.get_object(BLUEZ_SERVICE_NAME, self.adapter), 
            LE_ADVERTISING_MANAGER_IFACE 
        ) 
 
        manager.RegisterAdvertisement( 
            self.adv.get_path(), {}, 
            reply_handler=self.register_ad_cb, 
            error_handler=self.register_ad_error_cb 
        ) 
 
    def register_ad_cb(self): 
        logging.info('Advertisement registered') 
 
    def register_ad_error_cb(self, error): 
        logging.error(f'Failed to register advertisement: {str(error)}') 
        if self.mainloop: 
            self.mainloop.quit() 
 
    def run(self): 
        self.mainloop = GLib.MainLoop() 
        self.mainloop.run() 
 
 
def radar_reader_thread(radar: RD03DRadar, data_queue: queue.Queue, stop_event: threading.Event): 
    while not stop_event.is_set(): 
        frame = radar.read_frame() 
        if frame: 
            targets = radar.parse_targets(frame) 
            if targets: 
                data_queue.put(targets) 
 
 
def main(): 
    parser = argparse.ArgumentParser(description="RD-03D Radar BLE GATT Server") 
    parser.add_argument("--port", default="/dev/ttyUSB0", help="Serial port") 
    parser.add_argument("--baud", type=int, default=256000, help="Baud rate") 
    parser.add_argument("--adapter", default="hci0", help="Bluetooth adapter") 

    args = parser.parse_args() 
  
    # Set up logging 
    logging.basicConfig( 
        level=logging.INFO, 
        format='[%(asctime)s] %(levelname)s: %(message)s' 
    ) 

    # Initialize radar 
    radar = RD03DRadar(port=args.port, baudrate=args.baud) 
    data_queue: queue.Queue = queue.Queue(maxsize=10) 
    stop_event = threading.Event() 

    # Start radar reader thread 
    reader_thread = threading.Thread( 
        target=radar_reader_thread, 
        args=(radar, data_queue, stop_event), 
        daemon=True 
    ) 
    reader_thread.start() 
    logging.info("Radar reader thread started") 

    # Initialize and run BLE server 
    try: 
        server = RadarGattServer(args.adapter) 
        server.register_app(data_queue) 
        server.register_ad() 
        logging.info("BLE GATT server running. Press Ctrl+C to stop.") 
        server.run() 
    except KeyboardInterrupt: 
        logging.info("Shutting down...") 
    except Exception as e: 
        logging.error(f"Error: {e}") 
    finally: 
        stop_event.set() 
        reader_thread.join() 
        radar.close() 

if __name__ == "__main__": 
    main() 
