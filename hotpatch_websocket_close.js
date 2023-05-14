#!/bin/bash


for fn in build/js/packages/confido1/kotlin/ktor-ktor-client-core-js-ir.js \
            build/compileSync/main/developmentExecutable/kotlin/kotlin_io_ktor_ktor_client_core.js \
            build/compileSync/main/productionExecutable/kotlin/ktor-ktor-client-core-js-ir.js \
            build/klib/cache/ktor-client-core-jsir-*/*/module.js \
            build/js/packages/confido1/kotlin/kotlin_io_ktor_ktor_client_core.js \
; do

    if [[ -f "$fn" ]] && grep -qE 'close\(Codes_INTERNAL_ERROR_getInstance\(\).[a-zA-Z0-9_$]+(\(\))?' "$fn"; then
        echo >&2 "HOTPATCH $fn"
        sed -i -re 's#close\(Codes_INTERNAL_ERROR_getInstance\(\).[a-zA-Z0-9_$]+(\(\))?#close(1000#g' "$fn"
    fi
done

## XX does not work
# for fn in build/js/packages/confido1/kotlin/ktor-ktor-websockets-js-ir.js \
#             build/compileSync/main/developmentExecutable/kotlin/kotlin_io_ktor_ktor_websockets.js \
#             build/compileSync/main/productionExecutable/kotlin/ktor-ktor-websockets-js-ir.js \
#             build/klib/cache/ktor-websockets-jsir-*/*/module.js \
#             build/js/packages/confido1/kotlin/kotlin_io_ktor_ktor_websockets.js \
# ; do
# 
#     if [[ -f "$fn" ]]; then
#         echo >&2 "HOTPATCH $fn"
#         sed -i -re 's#new Codes\('\''(INTERNAL_ERROR)'\'',\s*([^,]+),\s*(1[0-9]{3})\)#new Codes(\1, \2, 1000)#g' "$fn"
#     fi
# done

