# Static concept poses for Art Bible style-frame bases (written to build/resources, never to src).
import json, sys, os
OUT = sys.argv[1]
LEGS = {"rightLeg": {"pitch": -86.0, "yaw": 4.0, "roll": 3.0, "bend": 86.0},
        "leftLeg": {"pitch": -86.0, "yaw": -4.0, "roll": -3.0, "bend": 86.0}}
POSES = {

 "sb_ta": {"head": {"pitch": 6, "yaw": 0, "roll": -8}, "torso": {"pitch": 0, "yaw": 0, "roll": 0, "bend": 6},
             "body": {"pitch": -4, "yaw": 0, "roll": 0, "x": 0, "y": 0, "z": 0},
             "rightArm": {"pitch": -100, "yaw": -70, "roll": 0, "x": -5, "y": 2, "z": -0.5, "bend": -110},
             "leftArm": {"pitch": -45, "yaw": 12, "roll": -4, "x": 5, "y": 2, "z": -0.5, "bend": -56}},
 "sb_tb": {"head": {"pitch": 6, "yaw": 0, "roll": -8}, "torso": {"pitch": 0, "yaw": 0, "roll": 0, "bend": 6},
             "body": {"pitch": -4, "yaw": 0, "roll": 0, "x": 0, "y": 0, "z": 0},
             "rightArm": {"pitch": -170, "yaw": 0, "roll": 40, "x": -5, "y": 2, "z": -0.5, "bend": -90},
             "leftArm": {"pitch": -45, "yaw": 12, "roll": -4, "x": 5, "y": 2, "z": -0.5, "bend": -56}},
 "sb_tc": {"head": {"pitch": 6, "yaw": 0, "roll": -8}, "torso": {"pitch": 0, "yaw": 0, "roll": 0, "bend": 6},
             "body": {"pitch": -4, "yaw": 0, "roll": 0, "x": 0, "y": 0, "z": 0},
             "rightArm": {"pitch": -90, "yaw": -80, "roll": -10, "x": -5, "y": 2, "z": -0.5, "bend": 110},
             "leftArm": {"pitch": -45, "yaw": 12, "roll": -4, "x": 5, "y": 2, "z": -0.5, "bend": -56}},
 "sb_slam": {"head": {"pitch": -14, "yaw": 0, "roll": 0}, "torso": {"pitch": 0, "yaw": 0, "roll": 0, "bend": 14},
             "body": {"pitch": -22, "yaw": 0, "roll": 0, "x": 0, "y": 0, "z": 0},
             "rightArm": {"pitch": -78, "yaw": -4, "roll": 0, "x": -5, "y": 2, "z": -0.5, "bend": -12},
             "leftArm": {"pitch": -128, "yaw": 22, "roll": 0, "x": 5, "y": 2, "z": -0.5, "bend": 0}},
 "sb_flinch": {"head": {"pitch": -6, "yaw": 0, "roll": 0}, "torso": {"pitch": 0, "yaw": 0, "roll": 0, "bend": -4},
             "body": {"pitch": 10, "yaw": 0, "roll": 0, "x": 0, "y": 0, "z": 0},
             "rightArm": {"pitch": -38, "yaw": -18, "roll": 6, "x": -5, "y": 2, "z": -0.5, "bend": -50},
             "leftArm": {"pitch": -38, "yaw": 18, "roll": -6, "x": 5, "y": 2, "z": -0.5, "bend": -50}},
 "sb_win": {"head": {"pitch": -6, "yaw": 0, "roll": 0}, "torso": {"pitch": 0, "yaw": 0, "roll": 0, "bend": -2},
             "body": {"pitch": 8, "yaw": 0, "roll": 0, "x": 0, "y": 0, "z": 0},
             "rightArm": {"pitch": -48, "yaw": -14, "roll": 4, "x": -5, "y": 2, "z": -0.5, "bend": -45},
             "leftArm": {"pitch": -40, "yaw": 16, "roll": -4, "x": 5, "y": 2, "z": -0.5, "bend": -55}},
}
for name, p in POSES.items():
    p = dict(p, **LEGS)
    moves = []
    for t in (0, 20):
        for part, v in p.items():
            moves.append({"tick": t, "easing": "LINEAR", "turn": 0, part: v})
    d = {"version": 3, "name": name, "author": "artbible", "sounds": "",
         "emote": {"isLoop": True, "returnTick": 0, "beginTick": 0, "endTick": 20, "stopTick": 21,
                   "degrees": True, "easeBeforeKeyframe": True, "moves": moves}}
    json.dump(d, open(os.path.join(OUT, name + ".json"), "w"))
print("ok")
