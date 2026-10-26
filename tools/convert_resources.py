#!/usr/bin/env python3
"""Migrate the bundled MightyPirates assets to Minecraft 1.21.1 resources.

Run from any directory with Python 3. Sources are the upstream .lang/.recipes
files and original item registrations; Lua, documentation and textures are kept.
This is a format converter, not a substitute for the runtime recipe serializers
or the custom block/item renderers.
"""
from __future__ import annotations

import argparse
import copy
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/opencomputers'
DATA = ROOT / 'src/main/resources/data/opencomputers'
UPSTREAM_COMMIT = '258943363ea8fc4ef14948e6bc6f67375061580c'
UPSTREAM_ROOT: Path | None = None
HARDWARE_TOOLTIPS = {
    'hardware_tier': ('Component slot: Tier %s', '所需组件槽位：T%s'),
    'cpu_components': ('Supported components: %s', '支持的组件数：%s'),
    'processor_configure': ('Sneak + right-click to change architecture', '潜行并右键切换处理器架构'),
    'memory_size': ('Memory: %s KiB', '内存容量：%s KiB'),
    'gpu_resolution': ('Maximum resolution: %s × %s', '最大分辨率：%s × %s'),
    'gpu_depth': ('Maximum color depth: %s-bit', '最大色深：%s 位'),
    'gpu_operations': ('Operations/tick: %s', '操作数 / tick：%s'),
    'storage_size': ('Capacity: %s KiB', '存储容量：%s KiB'),
}
PORT_ITEM_NAMES = {
    'en_us': {'item.opencomputers.floppycontainer': 'Floppy Disk Container'},
    'zh_cn': {'item.opencomputers.floppycontainer': '软盘容器'},
}


def upstream_source(relative: str) -> str:
    """Read the original source without requiring Scala in this Java project."""
    if UPSTREAM_ROOT is not None:
        source = UPSTREAM_ROOT / relative
        if not source.exists() and relative.startswith('src/main/'):
            source = UPSTREAM_ROOT / relative.removeprefix('src/main/')
        if source.is_file():
            return source.read_text(encoding='utf-8')
    result = subprocess.run(['git', '-C', str(ROOT), 'show', f'{UPSTREAM_COMMIT}:{relative}'],
                            text=True, encoding='utf-8', capture_output=True)
    if result.returncode == 0:
        return result.stdout
    # Also support an initial, not-yet-imported upstream checkout.
    source = ROOT / relative
    if source.is_file():
        return source.read_text(encoding='utf-8')
    raise FileNotFoundError(f'Original source unavailable: {relative}; pass --upstream-root or fetch {UPSTREAM_COMMIT}')


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def constants() -> dict[str, str]:
    text = upstream_source('src/main/scala/li/cil/oc/Constants.scala')
    return dict(re.findall(r'final val (\w+) = "([a-z0-9_]+)"', text))


def item_translations(ids: dict[str, str]) -> dict[str, str]:
    """Resolve original class/tier translation keys, including zero based tiers."""
    text = upstream_source('src/main/scala/li/cil/oc/common/init/Items.scala')
    tiers = {'One': '0', 'Two': '1', 'Three': '2', 'Four': '3', 'Five': '4', 'Six': '5'}
    result: dict[str, str] = {}
    for kind, args, constant in re.findall(
        r'(?:registerItem|addSubItem|addItem)\(new item\.(\w+)\(([^\n]*?)\), Constants\.ItemName\.(\w+)', text
    ):
        source_text = upstream_source(f'src/main/scala/li/cil/oc/common/item/{kind}.scala')
        override = re.search(r'override def unlocalizedName\s*=\s*"([^"]+)"', source_text)
        name = override.group(1) if override else kind.lower()
        tier = re.search(r'Tier\.(\w+)', args)
        if tier and 'unlocalizedName' in source_text and '+ tier' in source_text:
            name += tiers[tier.group(1)]
        result[ids[constant]] = f'item.oc.{name}.name'
    return result


def language() -> int:
    ids = constants()
    item_keys = item_translations(ids)
    total = 0
    for source in sorted((ASSETS / 'lang').glob('*.lang')):
        translations = {}
        for line in source.read_text(encoding='utf-8-sig').splitlines():
            if not line.strip() or line.lstrip().startswith('#') or '=' not in line:
                continue
            key, value = line.split('=', 1)
            translations[key.strip()] = value
        # Keep original keys for the migrated manual/tooltips and add modern
        # registry-derived descriptions. Do not confuse tier 0 with tier 1.
        for key, value in list(translations.items()):
            if key.startswith('tile.oc.') and key.endswith('.name'):
                translations['block.opencomputers.' + key[8:-5]] = value
        for registry_id, legacy_key in item_keys.items():
            if legacy_key in translations:
                translations['item.opencomputers.' + registry_id] = translations[legacy_key]
        if 'itemGroup.opencomputers' not in translations:
            translations['itemGroup.opencomputers'] = 'OpenComputer-Reloaded'
        translations['gui.opencomputers.turn_on'] = '启动' if source.stem.lower() == 'zh_cn' else 'Turn on'
        translations['gui.opencomputers.turn_off'] = '关闭' if source.stem.lower() == 'zh_cn' else 'Turn off'
        for key, (english, chinese) in HARDWARE_TOOLTIPS.items():
            translations['tooltip.opencomputers.' + key] = chinese if source.stem.lower() == 'zh_cn' else english
        # Keep previews already running with the previous component bus key usable on resource reload.
        translations['oc:tooltip.cpu.Components'] = translations['tooltip.opencomputers.cpu_components']
        translations.update(PORT_ITEM_NAMES.get(source.stem.lower(), {}))
        write_json(source.with_name(source.stem.lower() + '.json'), translations)
        total += len(translations)
    return total


def model_reference(ref: str, block: bool = False) -> str:
    namespace, sep, path = ref.partition(':')
    if not sep:
        namespace, path = 'minecraft', namespace
    if block and not path.startswith(('block/', 'item/', 'builtin/')):
        path = 'block/' + path
    return namespace + ':' + path


def models() -> None:
    for source in (ASSETS / 'blockstates').glob('*.json'):
        data = json.loads(source.read_text())
        if 'variants' in data and 'normal' in data['variants']:
            data['variants'][''] = data['variants'].pop('normal')
        if 'variants' in data and 'color=silver' in data['variants']:
            data['variants']['color=light_gray'] = data['variants'].pop('color=silver')
        def fix(value):
            if isinstance(value, dict):
                for key, child in list(value.items()):
                    if key == 'model' and isinstance(child, str):
                        value[key] = model_reference(child, block=True)
                    elif key in ('x', 'y') and isinstance(child, int):
                        value[key] = child % 360
                    else:
                        fix(child)
            elif isinstance(value, list):
                for child in value:
                    fix(child)
        fix(data)
        write_json(source, data)
    for source in (ASSETS / 'models').rglob('*.json'):
        data = json.loads(source.read_text())
        if 'parent' in data:
            data['parent'] = model_reference(data['parent'])
        display = data.get('display', {})
        original_display = {}
        if display:
            original_display = json.loads(upstream_source(str(source.relative_to(ROOT)))).get('display', {})
        for old, new in [('thirdperson', 'thirdperson_righthand'), ('firstperson', 'firstperson_righthand')]:
            # These are 1.8 keys, already ignored by the original 1.12 loader.
            # Activating their old rotations in a modern hand transform turns
            # the original blocks upside down. Retain valid inherited poses.
            display.pop(old, None)
            if old in original_display and new not in original_display:
                display.pop(new, None)
        if 'display' in data and not display:
            data.pop('display')
        write_json(source, data)
    # Original renderer selected this model under the legacy name 'switch'.
    write_json(ASSETS / 'blockstates/netsplitter.json', {'variants': {'': {'model': 'opencomputers:block/switch'}}})
    write_json(ASSETS / 'models/item/netsplitter.json', {'parent': 'opencomputers:item/switch'})
    # Block and item atlas sources preserve original plural texture paths used
    # by the custom mesh renderers; renaming PNGs would break those references.
    write_json(ROOT / 'src/main/resources/assets/minecraft/atlases/blocks.json', {'sources': [
        {'type': 'minecraft:directory', 'source': 'blocks', 'prefix': 'blocks/'},
        {'type': 'minecraft:directory', 'source': 'items', 'prefix': 'items/'},
    ]})
    # Base inventory models are now selected by registry id. Stateful textures
    # still require the client ItemProperties registrations (running/color).
    for name in ('terminal', 'tablet'):
        write_json(ASSETS / f'models/item/{name}.json', {
            'parent': f'opencomputers:item/{name}_off',
            'overrides': [{'predicate': {'opencomputers:running': 1}, 'model': f'opencomputers:item/{name}_on'}],
        })
    floppy_colors = [p.stem.removeprefix('floppy_') for p in (ASSETS / 'models/item').glob('floppy_*.json')]
    # Preserve the original dye-damage order, not modern DyeColor.getId().
    # Color.dyes: black=0, lightGray=7, gray=8 (the default), white=15.
    colors = ['black', 'red', 'green', 'brown', 'blue', 'purple', 'cyan', 'lightGray',
              'gray', 'pink', 'lime', 'yellow', 'lightBlue', 'magenta', 'orange', 'white']
    color_models = {name.removeprefix('dye').lower(): name for name in floppy_colors}
    default = color_models.get('gray')
    if default:
        write_json(ASSETS / 'models/item/floppy.json', {
            'parent': f'opencomputers:item/floppy_{default}',
            'overrides': [
                {'predicate': {'opencomputers:color': index / 15}, 'model': f'opencomputers:item/floppy_{color_models[color.lower()]}'}
                for index, color in enumerate(colors) if color.lower() in color_models
            ],
        })
    sounds = json.loads((ASSETS / 'sounds.json').read_text())
    for entry in sounds.values():
        entry.pop('category', None)  # SoundSource is selected by runtime now.
    write_json(ASSETS / 'sounds.json', sounds)


def screen_models():
    """Actual single-screen exterior, replacing the unused old generic cube.

    Multiblock face selection uses the Java ScreenBakedModel. These precise
    unit models supply inventory and render-without-block-entity fallbacks.
    """
    directions = ['down', 'up', 'north', 'south', 'west', 'east']
    yaw_indices = {'south': 0, 'west': 1, 'north': 2, 'east': 3}
    variants = {}
    for pitch in ['north', 'up', 'down']:
        for yaw, rotation in yaw_indices.items():
            front = yaw if pitch == 'north' else pitch
            faces = {}
            for side in directions:
                face = {'texture': '#front' if side == front else '#b' if side in ['up', 'down'] else '#b2',
                        'cullface': side, 'tintindex': 0}
                if side == 'up':
                    face['rotation'] = rotation * 90 % 360
                elif side == 'down':
                    face['rotation'] = -rotation * 90 % 360
                faces[side] = face
            name = f'screen_{pitch}_{yaw}'
            write_json(ASSETS / 'models/block' / f'{name}.json', {
                'parent': 'minecraft:block/block',
                'textures': {'front': 'opencomputers:blocks/screen/f' if pitch == 'north' else 'opencomputers:blocks/screen/f2',
                             'b': 'opencomputers:blocks/screen/b', 'b2': 'opencomputers:blocks/screen/b2',
                             'particle': 'opencomputers:blocks/screen/b2'},
                'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces}],
            })
            variants[f'pitch={pitch},yaw={yaw}'] = {'model': 'opencomputers:block/' + name}
    for tier in range(1, 4):
        write_json(ASSETS / 'blockstates' / f'screen{tier}.json', {'variants': variants})
        write_json(ASSETS / 'models/item' / f'screen{tier}.json', {'parent': 'opencomputers:block/screen_north_north'})


def cable_models():
    """Port CableModel's wire/device plugs and isolated ends to multipart JSON."""
    directions = ['down', 'up', 'north', 'south', 'west', 'east']
    opposite = {'down': 'up', 'up': 'down', 'north': 'south', 'south': 'north', 'west': 'east', 'east': 'west'}
    def box(start, end, cap=False):
        faces = {direction: {'texture': '#cap' if cap else '#wire'} for direction in directions}
        if not cap:
            for face in faces.values():
                face['tintindex'] = 0
        return {'from': start, 'to': end, 'faces': faces}
    def model(name, elements):
        write_json(ASSETS / 'models/block' / f'{name}.json', {
            'parent': 'minecraft:block/block', 'ambientocclusion': False,
            'textures': {'wire': 'opencomputers:blocks/cable', 'cap': 'opencomputers:blocks/cablecap',
                         'particle': 'opencomputers:blocks/cable'}, 'elements': elements,
        })
    model('cable_core', [box([6, 6, 6], [10, 10, 10])])
    dimensions = {
        'down': ([6, 0, 6], [10, 6, 10], [6, 1, 6], [10, 6, 10], [5, 0, 5], [11, 1, 11], [6, 5, 6], [10, 6, 10]),
        'up': ([6, 10, 6], [10, 16, 10], [6, 10, 6], [10, 15, 10], [5, 15, 5], [11, 16, 11], [6, 10, 6], [10, 11, 10]),
        'north': ([6, 6, 0], [10, 10, 6], [6, 6, 1], [10, 10, 6], [5, 5, 0], [11, 11, 1], [6, 6, 5], [10, 10, 6]),
        'south': ([6, 6, 10], [10, 10, 16], [6, 6, 10], [10, 10, 15], [5, 5, 15], [11, 11, 16], [6, 6, 10], [10, 10, 11]),
        'west': ([0, 6, 6], [6, 10, 10], [1, 6, 6], [6, 10, 10], [0, 5, 5], [1, 11, 11], [5, 6, 6], [6, 10, 10]),
        'east': ([10, 6, 6], [16, 10, 10], [10, 6, 6], [15, 10, 10], [15, 5, 5], [16, 11, 11], [10, 6, 6], [11, 10, 10]),
    }
    parts = [{'apply': {'model': 'opencomputers:block/cable_core'}}]
    for direction, (long_from, long_to, short_from, short_to, plug_from, plug_to, cap_from, cap_to) in dimensions.items():
        model('cable_' + direction + '_wire', [box(long_from, long_to)])
        model('cable_' + direction + '_plug', [box(short_from, short_to), box(plug_from, plug_to, cap=True)])
        model('cable_' + direction + '_cap', [box(cap_from, cap_to, cap=True)])
        parts.extend([
            {'when': {direction: 'true', direction + '_cable': 'true'}, 'apply': {'model': f'opencomputers:block/cable_{direction}_wire'}},
            {'when': {direction: 'true', direction + '_cable': 'false'}, 'apply': {'model': f'opencomputers:block/cable_{direction}_plug'}},
            {'when': {other: 'false' for other in directions if other != opposite[direction]},
             'apply': {'model': f'opencomputers:block/cable_{direction}_cap'}},
        ])
    write_json(ASSETS / 'blockstates/cable.json', {'multipart': parts})
    model('cable_inventory', [box([6, 6, 6], [10, 10, 10]), box([6, 1, 6], [10, 6, 10]),
                              box([6, 10, 6], [10, 15, 10]), box([5, 0, 5], [11, 1, 11], cap=True),
                              box([5, 15, 5], [11, 16, 11], cap=True)])
    write_json(ASSETS / 'models/item/cable.json', {'parent': 'opencomputers:block/cable_inventory'})


def pack_metadata():
    path = ROOT / 'src/main/resources/pack.mcmeta'
    data = json.loads(path.read_text()) if path.exists() else {}
    data.setdefault('pack', {})['pack_format'] = 34
    data['pack'].setdefault('description', 'OpenComputer-Reloaded resources by MightyPirates and contributors')
    data.setdefault('language', {}).update({
        'de_de': {'name': 'Deutsch', 'region': 'Deutschland', 'bidirectional': False},
        'en_us': {'name': 'English', 'region': 'United States', 'bidirectional': False},
        'es_mx': {'name': 'Español', 'region': 'México', 'bidirectional': False},
        'fr_fr': {'name': 'Français', 'region': 'France', 'bidirectional': False},
        'it_it': {'name': 'Italiano', 'region': 'Italia', 'bidirectional': False},
        'pl_pl': {'name': 'Polski', 'region': 'Polska', 'bidirectional': False},
        'pt_br': {'name': 'Português', 'region': 'Brasil', 'bidirectional': False},
        'pt_pt': {'name': 'Português', 'region': 'Portugal', 'bidirectional': False},
        'ru_ru': {'name': 'Русский', 'region': 'Россия', 'bidirectional': False},
        'tr_tr': {'name': 'Türkçe', 'region': 'Türkiye', 'bidirectional': False},
        'zh_cn': {'name': '简体中文', 'region': '中国', 'bidirectional': False},
        'zh_tw': {'name': '繁體中文', 'region': '台灣', 'bidirectional': False},
    })
    write_json(path, data)


class RecipeConfig:
    """Parser for the HOCON subset used by upstream's bundled recipe sets.

    Handles comments, omitted commas, nested arrays/objects and local includes;
    fails on unknown syntax rather than silently dropping recipe ingredients.
    """
    TOKEN = re.compile(r'\s+|\#[^\n]*|//[^\n]*|"(?:\\.|[^"\\])*"|[{}\[\]:=,()]|[^\s{}\[\]:=,()#]+')

    def __init__(self, directory: Path):
        self.directory = directory
        self.cache = {}

    def load(self, filename: str) -> dict:
        if filename in self.cache:
            return copy.deepcopy(self.cache[filename])
        text = (self.directory / filename).read_text()
        # Includes have the precedence of their position in the file.
        tokens = [m.group() for m in self.TOKEN.finditer(text)
                  if not m.group().isspace() and not m.group().startswith(('#', '//'))]
        pos = 0

        def take():
            nonlocal pos
            if pos >= len(tokens):
                raise ValueError(f'{filename}: unexpected end of recipe file')
            token = tokens[pos]
            pos += 1
            return token

        def scalar(token):
            if token.startswith('"'):
                return json.loads(token)
            if token in ('true', 'false'):
                return token == 'true'
            try:
                return int(token)
            except ValueError:
                return token

        def merge(target, key, value):
            if isinstance(value, dict) and isinstance(target.get(key), dict):
                for child_key, child_value in value.items():
                    merge(target[key], child_key, child_value)
            else:
                target[key] = value

        def value():
            token = take()
            if token == '{':
                return object_value('}')
            if token == '[':
                result = []
                while tokens[pos] != ']':
                    if tokens[pos] == ',':
                        take()
                    else:
                        result.append(value())
                take()
                return result
            return scalar(token)

        def object_value(end=None):
            result = {}
            while pos < len(tokens) and (end is None or tokens[pos] != end):
                token = take()
                if token == ',':
                    continue
                if token == 'include':
                    if take() != 'file' or take() != '(':
                        raise ValueError(f'{filename}: unsupported include')
                    include = scalar(take())
                    if take() != ')':
                        raise ValueError(f'{filename}: bad include')
                    for key, val in self.load(include).items():
                        merge(result, key, val)
                    continue
                key = scalar(token)
                if pos < len(tokens) and tokens[pos] in (':', '='):
                    take()
                merge(result, key, value())
            if end:
                if take() != end:
                    raise ValueError(f'{filename}: missing {end}')
            return result
        result = object_value()
        self.cache[filename] = result
        return copy.deepcopy(result)


def source_registration_aliases(ids):
    result = {}
    for name in ('Items', 'Blocks'):
        text = upstream_source(f'src/main/scala/li/cil/oc/common/init/{name}.scala')
        for constant, line in re.findall(r'Constants\.(?:ItemName|BlockName)\.(\w+),([^\n]*)', text):
            if constant in ids:
                for alias in re.findall(r'"(oc:[^"]+)"', line):
                    result[alias] = ids[constant]
    # Actual old OreDictionary aliases registered by compatibility code.
    result.update({'oc:wlanCard': 'wlancard2'})
    return result


def tags_and_drops():
    ids = constants()
    aliases = source_registration_aliases(ids)
    for identifier in sorted(set(aliases.values())):
        write_json(DATA / 'tags/item' / f'{identifier}.json', {
            'replace': False, 'values': [{'id': 'opencomputers:' + identifier, 'required': False}],
        })
    text = upstream_source('src/main/scala/li/cil/oc/Constants.scala')
    block_section = text.split('object BlockName {', 1)[1].split('object ItemName {', 1)[0]
    blocks = re.findall(r'final val \w+ = "([a-z0-9_]+)"', block_section)
    custom_drops = {'cable', 'microcontroller', 'print', 'raid', 'robot', 'robotafterimage'}
    states = {}
    for path in (ASSETS / 'blockstates').glob('*.json'):
        properties = {}
        for variant in json.loads(path.read_text()).get('variants', {}):
            for setting in variant.split(','):
                if '=' in setting:
                    key, value = setting.split('=', 1)
                    properties.setdefault(key, set()).add(value)
        if properties:
            states[path.stem] = {key: sorted(values) for key, values in properties.items()}
    write_json(ASSETS / 'resource-mapping.json', {
        'source': 'MightyPirates/OpenComputers@258943363ea8fc4ef14948e6bc6f67375061580c',
        'minecraft': '1.21.1', 'namespace': 'opencomputers',
        'blocks': blocks,
        'item_translations': item_translations(ids),
        'legacy_ore_aliases': aliases,
        'blockstate_properties': states,
        'client_item_properties': {
            'opencomputers:running': {'items': ['tablet', 'terminal'], 'values': [0, 1]},
            'opencomputers:color': {'items': ['floppy'], 'values': [value / 15 for value in range(16)],
                                   'data_values': list(range(16)), 'default': 8 / 15,
                                   'default_data': 8, 'order': list(reversed(DYES))},
        },
        'custom_drop_blocks': sorted(custom_drops),
    })
    # These blocks serialize their own data-bearing drops in CustomDrops or
    # RobotProxy. Their block implementations must keep that original logic.
    for identifier in blocks:
        table = {'type': 'minecraft:block', 'pools': [],
                 'neoforge:conditions': [{'type': 'neoforge:item_exists', 'item': 'opencomputers:' + identifier}]}
        if identifier not in custom_drops:
            entry = {'type': 'minecraft:item', 'name': 'opencomputers:' + identifier}
            if identifier == 'chameliumblock':
                entry['functions'] = [{'function': 'minecraft:copy_state',
                                       'block': 'opencomputers:chameliumblock', 'properties': ['color']}]
            table['pools'].append({'rolls': 1, 'entries': [entry],
                                   'conditions': [{'condition': 'minecraft:survives_explosion'}]})
        write_json(DATA / 'loot_table/blocks' / f'{identifier}.json', table)
    write_json(DATA.parent / 'minecraft/tags/block/mineable/pickaxe.json', {
        'replace': False,
        'values': [{'id': 'opencomputers:' + identifier, 'required': False} for identifier in blocks
                   if identifier not in {'print', 'robotafterimage'}],
    })


def recrafting():
    """Data-bearing crafting operations registered directly in Recipes.init."""
    def special(name, output, inputs, operation, count=1, **extra):
        data = {'type': 'opencomputers:crafting_shapeless', 'category': 'misc',
                'operation': operation, 'ingredients': inputs,
                'result': {'id': 'opencomputers:' + output, 'count': count}}
        data.update(extra)
        conditionally_registered(data)
        write_json(DATA / 'recipe/recrafting' / f'{name}.json', data)

    def item(name):
        return {'item': ('minecraft:' if name in {'filled_map', 'glowstone', 'glowstone_dust'} else 'opencomputers:') + name}

    special('navigation_map', 'navigationupgrade', [item('navigationupgrade'), item('filled_map')], 'navigation_map')
    special('eeprom_copy', 'eeprom', [item('eeprom'), item('eeprom')], 'copy_eeprom', count=2)
    special('linked_cards', 'linkedcard', [item('linkedcard'), item('linkedcard')], 'linked_cards', count=2)
    for device in ['microcontroller', 'drone', 'robot', 'tablet']:
        special(device + '_eeprom', device, [item(device), item('eeprom')], 'swap_eeprom')
    for disk in ['floppy', 'hdd1', 'hdd2', 'hdd3']:
        special(disk + '_format', disk, [item(disk)], 'format_disk')
    special('eeprom_format', 'eeprom', [item('eeprom')], 'format_eeprom')
    for legacy_index, dye in enumerate(reversed(DYES)):
        special('floppy_' + dye, 'floppy', [item('floppy'), {'tag': 'c:dyes/' + dye}],
                'color_floppy', color=legacy_index)
        special('chameliumblock_' + dye, 'chameliumblock',
                [item('chameliumblock'), {'tag': 'c:dyes/' + dye}], 'color_chamelium', color=dye)
    unpacking = {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [item('chameliumblock')], 'result': {'id': 'opencomputers:chamelium', 'count': 9},
    }
    conditionally_registered(unpacking)
    write_json(DATA / 'recipe/recrafting/chameliumblock_unpacking.json', unpacking)
    for block in ['iron_block', 'gold_block', 'emerald_block', 'diamond_block']:
        special('print_beacon_' + block, 'print', [item('print'), {'item': 'minecraft:' + block}], 'print_beacon')
    special('print_light_dust', 'print', [item('print'), item('glowstone_dust')], 'print_light', amount=1)
    special('print_light_block', 'print', [item('print'), item('glowstone')], 'print_light', amount=4)
    for target in ['hoverboots', 'cable']:
        for operation in ['colorize', 'decolorize']:
            dye_recipe = {
                'type': 'opencomputers:' + operation, 'category': 'misc', 'target': 'opencomputers:' + target,
            }
            conditionally_registered(dye_recipe)
            write_json(DATA / 'recipe/recrafting' / f'{target}_{operation}.json', dye_recipe)
    write_json(DATA / 'recipe/recrafting/loot_cycle.json', {'type': 'opencomputers:loot_cycle', 'category': 'misc'})


# Tags with no exact common standard are local tags containing the original
# vanilla ingredients; broad vanilla tags are used only when upstream did too.
ORE_INGREDIENTS = {
    'ingotIron': ('tag', 'c:ingots/iron'), 'ingotGold': ('tag', 'c:ingots/gold'),
    'nuggetGold': ('tag', 'c:nuggets/gold'), 'nuggetIron': ('tag', 'c:nuggets/iron'),
    'gemDiamond': ('tag', 'c:gems/diamond'), 'gemLapis': ('tag', 'c:gems/lapis'),
    'blockGlass': ('tag', 'c:glass_blocks/colorless'), 'paneGlass': ('tag', 'c:glass_panes/colorless'),
    'blockDiamond': ('tag', 'c:storage_blocks/diamond'), 'blockLapis': ('tag', 'c:storage_blocks/lapis'),
    'blockRedstone': ('tag', 'c:storage_blocks/redstone'), 'plankWood': ('tag', 'minecraft:planks'),
    'stickWood': ('tag', 'c:rods/wooden'), 'slimeball': ('tag', 'c:slime_balls'),
    'materialEnderPearl': ('tag', 'c:ender_pearls'), 'glassBottle': ('item', 'minecraft:glass_bottle'),
    'bucketWater': ('item', 'minecraft:water_bucket'), 'bucketLava': ('item', 'minecraft:lava_bucket'),
    'fenceIron': ('item', 'minecraft:iron_bars'), 'craftingPiston': ('item', 'minecraft:piston'),
    'pistonStickyBase': ('item', 'minecraft:sticky_piston'), 'torchRedstoneActive': ('item', 'minecraft:redstone_torch'),
    'workbench': ('item', 'minecraft:crafting_table'), 'yellowDust': ('item', 'minecraft:glowstone_dust'),
    'eyeOfEnder': ('item', 'minecraft:ender_eye'), 'daylightDetector': ('item', 'minecraft:daylight_detector'),
    'blazePowder': ('item', 'minecraft:blaze_powder'), 'fermentedSpiderEye': ('item', 'minecraft:fermented_spider_eye'),
    'blazeRod': ('item', 'minecraft:blaze_rod'),
    'button': ('item', 'minecraft:stone_button'), 'carpet': ('tag', 'minecraft:wool_carpets'),
    'netherquartz': ('tag', 'c:gems/quartz'), 'diode': ('item', 'minecraft:repeater'),
}
DYES = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
        'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']
for _color in DYES:
    _legacy = ''.join(part.title() for part in _color.split('_'))
    ORE_INGREDIENTS['dye' + _legacy] = ('tag', 'c:dyes/' + _color)
ORE_INGREDIENTS['dyeLightGray'] = ('tag', 'c:dyes/light_gray')
ORE_INGREDIENTS['chipDiamond'] = ('item', 'opencomputers:chipdiamond')


def ingredient(value, aliases):
    if isinstance(value, dict):
        if 'oreDict' in value:
            return ingredient(value['oreDict'], aliases)
        name = value.get('item', value.get('block'))
        sub_id = value.get('subID', 0)
        if name == 'coal' and sub_id == 1:
            return {'item': 'minecraft:charcoal'}
        if name in ('dyePowder', 'minecraft:dye') and isinstance(sub_id, int):
            # Before flattening, these dye IDs were raw crafting materials.
            dye = {0: 'ink_sac', 3: 'cocoa_beans', 4: 'lapis_lazuli', 15: 'bone_meal'}.get(
                sub_id, list(reversed(DYES))[sub_id] + '_dye')
            return {'item': 'minecraft:' + dye}
        if name in ('minecraft:wool', 'wool') and isinstance(sub_id, int):
            return {'item': 'minecraft:' + DYES[sub_id] + '_wool'}
        if sub_id not in (0, 'any'):
            raise ValueError(f'Legacy mod metadata needs an explicit modern mapping: {value}')
        return ingredient(name, aliases)
    if not isinstance(value, str):
        raise ValueError(f'Unsupported recipe ingredient: {value!r}')
    if not value:
        return None
    if value in aliases:
        return {'tag': 'opencomputers:' + aliases[value]}
    if value in ORE_INGREDIENTS:
        kind, identifier = ORE_INGREDIENTS[value]
        return {kind: identifier}
    if value.startswith('oc:'):
        raise ValueError(f'Unmapped upstream OC alias: {value}')
    if re.fullmatch('[a-z_]+', value):
        return {'item': 'minecraft:' + value}
    if re.fullmatch('[a-z0-9_.-]+:[a-z0-9_./-]+', value):
        return {'item': value}
    raise ValueError(f'Unmapped upstream ingredient: {value}')


def recipe_json(name, config, aliases):
    result_id = {'ingotiron': 'minecraft:iron_ingot', 'luabios': 'opencomputers:eeprom'}.get(name, 'opencomputers:' + name)
    result = {'id': result_id, 'count': config.get('output', 1)}
    kind = config.get('type', 'shaped')
    if kind == 'furnace':
        return {'type': 'minecraft:smelting', 'category': 'misc', 'ingredient': ingredient(config['input'], aliases),
                'result': result, 'experience': 0.0, 'cookingtime': 200}
    operation = None
    if name.startswith(('cpu', 'apu')):
        operation = 'default_architecture'
    elif name == 'navigationupgrade':
        operation = 'navigation_map'
    elif name == 'linkedcard':
        operation = 'linked_cards'
    elif name == 'luabios':
        operation = 'lua_bios'
    elif name.startswith('loot/'):
        operation = 'loot_disk'
        result['id'] = 'opencomputers:floppy'
    data = {'type': ('opencomputers:' if operation else 'minecraft:') + 'crafting_' + kind,
            'category': 'misc'}
    if operation:
        data['operation'] = operation
    if operation == 'loot_disk':
        data['disk'] = config['name']
    if kind == 'shapeless':
        data['ingredients'] = [ingredient(value, aliases) for value in config['input']]
        if None in data['ingredients']:
            raise ValueError(f'{name}: shapeless recipe contains empty ingredient')
    elif kind == 'shaped':
        chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
        keys = {}
        pattern = []
        for row in config['input']:
            pattern_row = ''
            for value in row:
                converted = ingredient(value, aliases)
                if converted is None:
                    pattern_row += ' '
                else:
                    encoded = json.dumps(converted, sort_keys=True)
                    if encoded not in keys:
                        keys[encoded] = chars[len(keys)]
                    pattern_row += keys[encoded]
            pattern.append(pattern_row)
        # ShapedRecipePattern shrinks edge whitespace; shrinking here prevents
        # a legacy 3x3 array from needlessly rejecting a valid 1x2 crafting grid.
        while pattern and not pattern[0].strip():
            pattern.pop(0)
        while pattern and not pattern[-1].strip():
            pattern.pop()
        left = min(len(row) - len(row.lstrip()) for row in pattern)
        right = max(len(row.rstrip()) for row in pattern)
        data['pattern'] = [row[left:right] for row in pattern]
        data['key'] = {symbol: json.loads(encoded) for encoded, symbol in keys.items()}
    else:
        raise ValueError(f'Custom machine recipe type needs its integration bridge: {kind}')
    data['result'] = result
    return data


def conditionally_registered(data):
    """Retain pending module definitions without invalidating live registries."""
    required = set()
    def visit(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key == 'neoforge:conditions':
                    continue
                if key in ('item', 'id', 'target') and isinstance(child, str) and child.startswith('opencomputers:'):
                    required.add(child)
                else:
                    visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)
    visit(data)
    conditions = data.setdefault('neoforge:conditions', [])
    for item in sorted(required):
        condition = {'type': 'neoforge:item_exists', 'item': item}
        if condition not in conditions:
            conditions.append(condition)


def recipes(profile='default'):
    aliases = source_registration_aliases(constants())
    config = RecipeConfig(ASSETS / 'recipes').load(profile + '.recipes')
    generated = []
    unresolved = []
    for name, spec in config.items():
        if isinstance(spec, bool):
            continue
        if name == 'lootdisks':
            entries = [('loot/' + spec['name'].split(':', 1)[1], spec) for spec in spec]
        elif isinstance(spec, dict):
            entries = [(name.lower(), spec)]
        else:
            unresolved.append({'name': name, 'reason': 'Unsupported top level value', 'source': spec})
            continue
        for identifier, recipe in entries:
            try:
                converted = recipe_json(identifier, recipe, aliases)
                converted['neoforge:conditions'] = [{'type': 'opencomputers:recipe_set', 'set': profile}]
                if identifier == 'worldsensorcard':
                    converted['neoforge:conditions'].extend([
                        {'type': 'neoforge:item_exists', 'item': 'opencomputers:worldsensorcard'},
                        {'type': 'neoforge:item_exists', 'item': 'galacticraftcore:sensor_lens'},
                    ])
                conditionally_registered(converted)
                write_json(DATA / 'recipe' / profile / f'{identifier}.json', converted)
                generated.append(identifier)
            except ValueError as exc:
                # Keep the complete original recipe in a machine readable source
                # manifest; inventing current registry IDs would alter gameplay.
                unresolved.append({'name': identifier, 'reason': str(exc), 'source': recipe})
    write_json(ASSETS / 'recipes' / f'{profile}.migration.json', {
        'source': 'MightyPirates/OpenComputers@258943363ea8fc4ef14948e6bc6f67375061580c',
        'profile': profile, 'generated': sorted(set(generated)), 'requires_integration_mapping': unresolved,
    })
    return len(set(generated)), unresolved


def main():
    global UPSTREAM_ROOT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--recipe-set', choices=['default', 'hardmode', 'peaceful', 'gregtech'], default='default')
    parser.add_argument('--all-recipe-sets', action='store_true', help='Generate the four selectable upstream recipe sets')
    parser.add_argument('--upstream-root', type=Path, help='Original MightyPirates checkout; defaults to the pinned Git commit')
    options = parser.parse_args()
    UPSTREAM_ROOT = options.upstream_root.resolve() if options.upstream_root else None
    total = language()
    models()
    screen_models()
    cable_models()
    pack_metadata()
    tags_and_drops()
    recrafting()
    profiles = ['default', 'hardmode', 'peaceful', 'gregtech'] if options.all_recipe_sets else [options.recipe_set]
    for profile in profiles:
        generated, unresolved = recipes(profile)
        print(f'Converted translations ({total} entries), models/blockstates and {generated} {profile} recipes.')
        for entry in unresolved:
            print(f"Requires integration mapping: {entry['name']}: {entry['reason']}")


if __name__ == '__main__':
    main()
