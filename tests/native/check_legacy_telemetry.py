import json,subprocess,sys,math
rows=[json.loads(line) for line in subprocess.check_output([sys.argv[1]],text=True).splitlines()]
assert len(rows)==8
assert rows[0]=={'section':'charge','values':{}},'missing is not zero'
x=rows[1]['values'];assert x['battery_level']==0 and x['charger_power']==0,'present zero preserved'
assert x['battery_range']==100,'native retains protocol units for one explicit mapper conversion'
assert 'est_battery_range' not in x,'NaN cannot become numeric telemetry'
assert x['charging_state']=='Stopped'
x=rows[2]['values'];assert x['is_climate_on'] is False;assert x['outside_temp_celsius']==-3.5;assert 'inside_temp_celsius' not in x
x=rows[3]['values'];assert math.isclose(x['tpms_pressure_fl'],2.45,abs_tol=0.00001);assert 'tpms_pressure_fr' not in x
x=rows[4]['values'];assert x['locked'] is False and x['sentry_mode'] is False
x=rows[5]['values'];assert x['odometer_in_hundredths_of_a_mile']==1234500 and x['shift_state']=='P'
x=rows[6]['values'];assert x['asleep'] is True and x['locked'] is False and x['user_present'] is False
assert rows[7]['values']['text']=='quote" newline\n slash\\'
print('PASS: real pinned protobuf fixture serialization, presence, zero/false, NaN omission, protocol units, enum mapping and JSON escaping')
