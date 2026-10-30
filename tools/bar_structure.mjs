// Struktur bar noir (Art Bible: set bar) untuk minecraft-builder-skill.
//
//   node ~/.claude/skills/minecraft-builder-skill/scripts/run-build.mjs tools/bar_structure.mjs \
//        --out-dir src/main/resources/data/liarsbar/structures --no-block-state-updates
//
// Koordinat di resep relatif pusat meja (x timur, z selatan, y = lantai tempat meja berdiri).
// Interior 17x15 (x -8..8, z -7..7), tinggi 5 (plafon di y 5, titik gantung lampu meja).
// Utara: back bar panjang. Barat: tiga jendela besar. Selatan: booth kulit. Timur: fasad etalase
// (pintu, dua jendela, marquee, kanopi, pilaster, cornice) menghadap trotoar dengan lampu jalan.
// BarStructure.TABLE_OFFSET = -MIN.

const MIN = [-10, -1, -9]
const SIZE = [24, 9, 19]
const L = 'liarsbar:'
const IX = 8 // setengah interior x
const IZ = 7 // setengah interior z
const WX = IX + 1 // dinding x
const WZ = IZ + 1 // dinding z
const TOP = 5 // plafon

export default function build(api) {
  const s = api.createStructure(SIZE)
  const at = (x, y, z) => [x - MIN[0], y - MIN[1], z - MIN[2]]
  const set = (x, y, z, block) => api.setBlock(s, at(x, y, z), block)
  const fill = (x0, y0, z0, x1, y1, z1, block) => api.fill(s, at(x0, y0, z0), at(x1, y1, z1), block)

  // ---------------------------------------------------------------- cangkang
  fill(-IX, 0, -IZ, IX, TOP - 1, IZ, 'minecraft:air')
  fill(-WX, -1, -WZ, WX, -1, WZ, L + 'bar_stone')
  fill(-IX, -1, -IZ, IX, -1, IZ, L + 'bar_floor')
  fill(-IX, TOP, -IZ, IX, TOP, IZ, L + 'bar_ceiling')

  const levels = ['wainscot', 'paper', 'paper', 'paper', 'crown']
  for (let y = 0; y < TOP; y++) {
    for (let z = -IZ; z <= IZ; z++) {
      set(-WX, y, z, `${L}bar_wall[facing=east,level=${levels[y]}]`)
      set(WX, y, z, `${L}bar_wall[facing=west,level=${levels[y]}]`)
    }
    for (let x = -IX; x <= IX; x++) {
      set(x, y, -WZ, `${L}bar_wall[facing=south,level=${levels[y]}]`)
      set(x, y, WZ, `${L}bar_wall[facing=north,level=${levels[y]}]`)
    }
  }
  for (const [x, z] of [[-WX, -WZ], [WX, -WZ], [-WX, WZ], [WX, WZ]]) {
    fill(x, 0, z, x, TOP, z, L + 'bar_stone')
  }
  // Bata di tingkat plafon, parapet batu di atasnya.
  for (let x = -WX; x <= WX; x++) {
    for (const z of [-WZ, WZ]) {
      if (Math.abs(x) < WX) set(x, TOP, z, L + 'bar_brick')
      set(x, TOP + 1, z, L + 'bar_stone')
    }
  }
  for (let z = -WZ; z <= WZ; z++) {
    for (const x of [-WX, WX]) {
      if (Math.abs(z) < WZ) set(x, TOP, z, L + 'bar_brick')
      set(x, TOP + 1, z, L + 'bar_stone')
    }
  }
  // Cornice tangga terbalik di keliling luar, sudut batu.
  const stairs = 'minecraft:polished_blackstone_brick_stairs'
  for (let z = -WZ; z <= WZ; z++) {
    set(WX + 1, TOP, z, `${stairs}[facing=west,half=top,shape=straight]`)
    set(-WX - 1, TOP, z, `${stairs}[facing=east,half=top,shape=straight]`)
  }
  for (let x = -WX; x <= WX; x++) {
    set(x, TOP, -WZ - 1, `${stairs}[facing=south,half=top,shape=straight]`)
    set(x, TOP, WZ + 1, `${stairs}[facing=north,half=top,shape=straight]`)
  }
  for (const [x, z] of [[WX + 1, -WZ - 1], [WX + 1, WZ + 1], [-WX - 1, -WZ - 1], [-WX - 1, WZ + 1]]) {
    set(x, TOP, z, 'minecraft:polished_blackstone_bricks')
  }
  // Pilaster batu di tiap sudut dan tengah dinding samping.
  const pilaster = (x, z) => fill(x, -1, z, x, TOP - 1, z, L + 'bar_stone')
  for (const z of [-WZ, WZ]) {
    pilaster(-WX - 1, z)
  }
  for (const x of [-WX, 0, WX]) {
    pilaster(x, -WZ - 1)
    pilaster(x, WZ + 1)
  }
  pilaster(-WX - 1, 0)

  // ---------------------------------------------------------------- jendela
  // Dari dalam: kiri = searah jarum jam dari arah interior (facing).
  const windowPair = (wallX, wallZ, facing, coords, scene) => {
    // coords: [[x, z, pane], ...] dua tile
    for (const [x, z, pane] of coords) {
      set(x, 1, z, `${L}noir_window[facing=${facing},pane=${pane},half=lower,scene=${scene}]`)
      set(x, 2, z, `${L}noir_window[facing=${facing},pane=${pane},half=upper,scene=${scene}]`)
    }
  }
  // Barat (facing east, kiri = selatan): RIGHT di z, LEFT di z+1.
  for (const [z, scene] of [[-6, 0], [-1, 1], [4, 0]]) {
    windowPair(-WX, 0, 'east', [[-WX, z, 'right'], [-WX, z + 1, 'left']], scene)
  }
  // Fasad timur (facing west, kiri = utara): LEFT di z, RIGHT di z+1.
  for (const z of [-5, 4]) {
    windowPair(WX, 0, 'west', [[WX, z, 'left'], [WX, z + 1, 'right']], 1)
  }
  // Utara: jendela tunggal di kedua ujung back bar.
  for (const x of [-IX, IX]) {
    set(x, 1, -WZ, `${L}noir_window[facing=south,pane=single,half=lower,scene=0]`)
    set(x, 2, -WZ, `${L}noir_window[facing=south,pane=single,half=upper,scene=0]`)
  }

  // ---------------------------------------------------------------- back bar (utara)
  const back = -IZ
  fill(-IX + 1, 0, back, IX - 1, 0, back, `${L}back_cabinet[facing=south]`)
  for (const x of [-6, -5, -4, -3, -2, 2, 3, 4, 5, 6]) {
    fill(x, 1, back, x, 3, back, `${L}bottle_shelf[facing=south]`)
  }
  for (let col = 0; col < 3; col++) {
    for (let row = 0; row < 2; row++) {
      // Menghadap selatan, dilihat dari selatan: kiri = barat.
      set(-1 + col, 1 + row, back, `${L}bar_mirror[facing=south,col=${col},row=${row}]`)
    }
  }
  set(0, 3, back, `${L}wall_sconce[facing=south]`)
  for (const x of [-7, 7]) {
    set(x, 3, back, `${L}wall_sconce[facing=south]`)
  }
  set(-IX, 0, back, 'minecraft:barrel[facing=up]')
  set(IX, 0, back, 'minecraft:barrel[facing=up]')
  // Counter panjang dengan footrail, lorong bartender di belakangnya, stool kulit di depannya.
  fill(-6, 0, back + 2, 6, 0, back + 2, `${L}bar_counter[facing=south]`)
  for (const x of [-6, -5, -4, -3, -2, 2, 3, 4, 5, 6]) {
    set(x, 0, back + 3, L + 'bar_stool')
  }

  // ---------------------------------------------------------------- barat
  // Meja bistro berdua di bawah jendela (kursi bentwood saling berhadapan), satu lagi di sisi timur.
  const bistro = (x, z) => {
    set(x, 0, z, L + 'bistro_table')
    set(x - 1, 0, z, `${L}bar_chair[facing=east]`)
    set(x + 1, 0, z, `${L}bar_chair[facing=west]`)
  }
  for (const z of [-3, 2]) {
    set(-IX, 2, z, `${L}wall_sconce[facing=east]`)
    bistro(-6, z)
  }
  bistro(6, 1)

  // ---------------------------------------------------------------- selatan: booth
  for (const [x0, tableX] of [[-7, -6], [4, 5]]) {
    for (let x = x0; x < x0 + 3; x++) {
      set(x, 0, IZ, `${L}bar_booth[facing=north]`)
    }
    set(tableX, 0, IZ - 1, L + 'side_table')
  }
  set(0, 0, IZ - 1, L + 'side_table')
  set(-1, 0, IZ - 1, L + 'bar_stool')
  set(1, 0, IZ - 1, L + 'bar_stool')
  const poster = (x, z, facing, design, y = 2) => {
    set(x, y, z, `${L}noir_poster[facing=${facing},design=${design},half=lower]`)
    set(x, y + 1, z, `${L}noir_poster[facing=${facing},design=${design},half=upper]`)
  }
  poster(-6, IZ, 'north', 'bluff')
  poster(5, IZ, 'north', 'pull')
  for (const x of [-3, 3]) {
    set(x, 2, IZ, `${L}wall_sconce[facing=north]`)
  }
  set(-IX, 0, IZ, 'minecraft:barrel[facing=up]')
  set(IX, 0, IZ, 'minecraft:barrel[facing=up]')
  set(IX, 1, IZ, 'minecraft:barrel[facing=north]')

  // ---------------------------------------------------------------- timur (dalam)
  poster(IX, -2, 'west', 'pull', 1)
  poster(IX, 2, 'west', 'bluff', 1)
  for (const z of [-6, 6]) {
    set(IX, 2, z, `${L}wall_sconce[facing=west]`)
  }
  set(WX, 0, 0, `${L}frosted_door[facing=west,half=lower,hinge=left,open=false,powered=false]`)
  set(WX, 1, 0, `${L}frosted_door[facing=west,half=upper,hinge=left,open=false,powered=false]`)

  // ---------------------------------------------------------------- plafon
  set(0, TOP - 1, 0, L + 'ceiling_fan')
  set(-5, TOP - 1, 3, L + 'ceiling_fan')
  set(5, TOP - 1, 3, L + 'ceiling_fan')
  fill(-7, TOP - 1, -5, -7, TOP - 1, IZ, `${L}iron_pipe[axis=z]`)
  fill(-6, TOP - 1, 5, IX, TOP - 1, 5, `${L}iron_pipe[axis=x]`)

  // ---------------------------------------------------------------- fasad timur
  const F = WX + 1
  for (const z of [-WZ, -3, 3, WZ]) {
    pilaster(F, z)
  }
  // Marquee 3 tile di atas pintu (menghadap timur, dilihat dari timur: kiri = selatan).
  for (let col = 0; col < 3; col++) {
    set(F, 3, 1 - col, `${L}bar_marquee[facing=east,col=${col}]`)
  }
  for (const z of [-1, 0, 1]) {
    set(F, 2, z, `${L}bar_awning[facing=east]`)
  }
  for (const z of [-5, -4, 4, 5]) {
    set(F, 3, z, `${L}bar_awning[facing=east]`)
  }
  for (const z of [-2, 2]) {
    set(F, 2, z, `${L}wall_sconce[facing=east]`)
  }
  set(F, 4, -6, `${L}bar_sign[facing=east,part=inner]`)
  set(F + 1, 4, -6, `${L}bar_sign[facing=east,part=outer]`)
  // Trotoar, undakan pintu, tepi jalan, lampu jalan.
  fill(F, -1, -WZ - 1, F + 2, -1, WZ + 1, 'minecraft:polished_deepslate')
  fill(F, -1, -1, F, -1, 1, L + 'bar_stone')
  fill(F + 3, -1, -WZ - 1, F + 3, -1, WZ + 1, 'minecraft:deepslate_tile_slab[type=top]')
  for (const z of [-7, 7]) {
    fill(F + 2, 0, z, F + 2, 2, z, 'minecraft:deepslate_tile_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
    set(F + 2, 3, z, 'minecraft:lantern[hanging=false,waterlogged=false]')
  }

  // ---------------------------------------------------------------- atap
  fill(-6, TOP + 1, 5, -6, TOP + 2, 5, L + 'bar_brick')

  return { structure: s, metadata: { name: 'bar' } }
}
