import pathlib,subprocess,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
HEADER=ROOT/'app/src/main/cpp/legacy_command_dispatch.h'
HARNESS=ROOT/'tests/native/legacy_command_dispatch_harness.cpp'
class LegacyNativeDispatchTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory();cls.executable=pathlib.Path(cls.tmp.name)/'dispatch'
  subprocess.run(['clang++','-std=c++17','-Wall','-Wextra','-Werror','-I'+str(HEADER.parent),str(HARNESS),'-o',str(cls.executable)],check=True,capture_output=True)
 @classmethod
 def tearDownClass(cls):cls.tmp.cleanup()
 def invoke(self,*args):return subprocess.check_output([str(self.executable),*args],text=True).strip()
 def test_flash_dispatch_requires_vehicle_ack(self):self.assertEqual(self.invoke('flash-lights'),'flash_lights ack=1 calls=1')
 def test_supported_operations_dispatch_correct_native_method_and_values(self):
  zero={'wake':'wake','honk':'honk_horn','lock':'lock','unlock':'unlock','frunk-open':'open_frunk','media-toggle-playback':'media_toggle_playback','software-update-cancel':'cancel_software_update'}
  for op,method in zero.items():
   with self.subTest(op=op):self.assertEqual(self.invoke(op),method+' ack=1 calls=1')
  cases=[('climate','on','set_climate:1'),('climate','off','set_climate:0'),('charging','start','set_charging_state:1'),('charging','stop','set_charging_state:0'),('sentry-mode','on','set_sentry_mode:1'),('steering-wheel-heater','off','set_steering_wheel_heat:0'),('trunk','open','open_trunk'),('trunk','close','close_trunk'),('charge-port','open','open_charge_port'),('charge-port','close','close_charge_port'),('charge-port','unlock','unlock_charge_port'),('windows','vent','vent_windows'),('windows','close','close_windows'),('charging-set-amps','32','set_charging_amps:32'),('charging-set-amps-override','4','set_charging_amps:4'),('charging-set-limit','80','set_charging_limit:80'),('climate-set-temp','20.5','set_climate_temp:20.5'),('media-set-volume','2.5','set_media_volume:2.5'),('software-update-start','600','schedule_software_update:600'),('heater-seat-front-left','med','set_seat_heater:7:2'),('heater-seat-front-right','high','set_seat_heater:8:3'),('heater-seat-rear-left','low','set_seat_heater:9:1'),('heater-seat-rear-right','off','set_seat_heater:12:0')]
  for op,value,method in cases:
   with self.subTest(op=op,value=value):self.assertEqual(self.invoke(op,value),method+' ack=1 calls=1')
 def test_low_amps_compatibility_is_two_acknowledged_idempotent_sets(self):self.assertEqual(self.invoke('charging-set-amps','4'),'set_charging_amps:4 ack=1 calls=2')
 def test_invalid_inputs_never_queue_a_native_operation(self):
  for args in [('lock','extra'),('lock;unlock',),('charging','on'),('climate','start'),('charging-set-amps','33'),('charging-set-amps','-1'),('climate-set-temp','NaN'),('climate-set-temp','Infinity'),('climate-set-temp','0x1p4'),('climate-set-temp','20;honk'),('media-set-volume','100'),('heater-seat-nonexistent','high'),('heater-seat-front-left','medium'),('software-update-start',),('software-update-start','-1')]:
   with self.subTest(args=args):self.assertEqual(self.invoke(*args),'REJECTED ack=0 called=')
